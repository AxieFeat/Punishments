package punishments.service.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.ScanArgs
import io.lettuce.core.XAddArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import punishments.service.config.RedisConfig
import java.util.concurrent.TimeUnit

class RedisCache(config: RedisConfig) {

    private val client: RedisClient = RedisClient.create(config.nodes.first())
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val commands: RedisCommands<String, String> = connection.sync()

    init {
        config.password?.takeIf(String::isNotBlank)?.let { password ->
            commands.auth(password)
        }
    }

    suspend fun get(key: String): String? = blocking {
        commands.get(key)
    }

    suspend fun set(key: String, value: String, ttlSeconds: Long) {
        blocking {
            commands.setex(key, ttlSeconds, value)
        }
    }

    suspend fun delete(key: String) {
        blocking {
            commands.del(key)
        }
    }

    suspend fun deleteByPattern(pattern: String) {
        blocking {
            val scanArgs = ScanArgs.Builder.matches(pattern).limit(SCAN_LIMIT)
            var cursor = commands.scan(scanArgs)
            while (true) {
                val keys = cursor.keys
                if (keys.isNotEmpty()) {
                    commands.del(*keys.toTypedArray())
                }
                if (cursor.isFinished) {
                    break
                }
                cursor = commands.scan(cursor, scanArgs)
            }
        }
    }

    suspend fun addStreamEntry(stream: String, fields: Map<String, String>, maxLength: Long) {
        blocking {
            commands.xadd(stream, XAddArgs().maxlen(maxLength).approximateTrimming(), fields)
        }
    }

    fun close() {
        connection.close()
        client.shutdown(0, 2, TimeUnit.SECONDS)
    }

    private suspend fun <T> blocking(block: () -> T): T {
        return withContext(Dispatchers.IO) { block() }
    }

    private companion object {
        const val SCAN_LIMIT = 100L
    }
}
