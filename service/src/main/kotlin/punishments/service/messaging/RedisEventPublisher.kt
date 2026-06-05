package punishments.service.messaging

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import punishments.common.event.PunishmentEvent
import punishments.service.cache.RedisCache

class RedisEventPublisher(
    private val redis: RedisCache,
    private val json: Json
) {

    private val logger = LoggerFactory.getLogger(RedisEventPublisher::class.java)

    suspend fun publish(event: PunishmentEvent) {
        try {
            redis.addStreamEntry(EVENT_STREAM, EventSerializer.serialize(event, json), STREAM_MAX_LENGTH)
        } catch (e: Exception) {
            logger.warn("Failed to publish punishment event {}", event.metadata.eventId, e)
        }
    }

    private companion object {
        const val EVENT_STREAM = "punishments:events"
        const val STREAM_MAX_LENGTH = 100_000L
    }
}
