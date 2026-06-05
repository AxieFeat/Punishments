package punishments.client.common.messaging

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import punishments.client.common.cache.ClientSideCache
import punishments.common.event.PunishmentEvent

class DualEventConsumer(
    private val redisConsumer: RedisEventConsumer,
    private val deduplicator: EventDeduplicator,
    private val eventHandler: EventHandler,
    private val cache: ClientSideCache
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(DualEventConsumer::class.java)

    fun start(scope: CoroutineScope) {
        redisConsumer.start(scope, ::processEvent)
        logger.info("DualEventConsumer started")
    }

    fun stop() {
        redisConsumer.stop()
    }

    override fun close() {
        redisConsumer.close()
    }

    suspend fun processEvent(event: PunishmentEvent) {
        if (!deduplicator.tryProcess(event.metadata.eventId)) {
            return
        }

        cache.invalidateAll()

        when (event) {
            is PunishmentEvent.PunishmentCreated -> eventHandler.onPunishmentCreated(event)
            is PunishmentEvent.PunishmentRevoked -> eventHandler.onPunishmentRevoked(event)
            is PunishmentEvent.PunishmentExpired -> eventHandler.onPunishmentExpired(event)
        }
    }
}
