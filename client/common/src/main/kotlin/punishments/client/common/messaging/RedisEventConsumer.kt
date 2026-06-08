package punishments.client.common.messaging

import io.lettuce.core.RedisClient
import io.lettuce.core.StreamMessage
import io.lettuce.core.XReadArgs
import io.lettuce.core.XReadArgs.StreamOffset
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import punishments.client.common.config.ClientConfig
import punishments.common.event.PunishmentEvent
import punishments.common.serialization.CommonSerializersModule
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class RedisEventConsumer(
    private val config: ClientConfig,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = CommonSerializersModule
    }
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(RedisEventConsumer::class.java)
    private val client: RedisClient = RedisClient.create(config.redisNodes.first())
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val commands: RedisCommands<String, String> = connection.sync()
    private var job: Job? = null

    init {
        config.redisPassword?.takeIf(String::isNotBlank)?.let { password ->
            commands.auth(password)
        }
    }

    fun start(scope: CoroutineScope, onEvent: suspend (PunishmentEvent) -> Unit) {
        check(job == null) { "Redis event consumer is already running" }

        job = scope.launch {
            logger.info("Redis event consumer started for server {}", config.serverId)
            var lastSeenId: String? = null

            while (isActive) {
                try {
                    val messages = readBatch(lastSeenId)
                    if (messages.isEmpty()) {
                        continue
                    }

                    for (message in messages) {
                        lastSeenId = message.id
                        decodeEvent(message.body)?.let { event ->
                            onEvent(event)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Redis consumer error, reconnecting in 3s", e)
                    delay(3_000.milliseconds)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    override fun close() {
        stop()
        connection.close()
        client.shutdown(0, 2, TimeUnit.SECONDS)
    }

    private suspend fun readBatch(lastSeenId: String?): List<StreamMessage<String, String>> {
        return withContext(Dispatchers.IO) {
            val args = XReadArgs.Builder.block(config.eventPollBlockMs).count(100)
            val offset = if (lastSeenId == null) {
                StreamOffset.latest(config.eventStreamKey)
            } else {
                StreamOffset.from(config.eventStreamKey, lastSeenId)
            }
            commands.xread(args, offset) ?: emptyList()
        }
    }

    private fun decodeEvent(fields: Map<String, String>): PunishmentEvent? {
        val payload = fields[DATA_FIELD] ?: return null
        return json.decodeFromString(payload)
    }

    private companion object {
        const val DATA_FIELD = "data"
    }
}
