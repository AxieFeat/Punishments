package punishments.service.domain.service

import org.slf4j.LoggerFactory
import punishments.common.event.EventMetadata
import punishments.common.event.PunishmentEvent
import punishments.service.cache.PunishmentCache
import punishments.service.config.AppConfig
import punishments.service.messaging.RedisEventPublisher
import punishments.service.persistence.repository.PunishmentRepository

class ExpirationService(
    private val repository: PunishmentRepository,
    private val events: RedisEventPublisher,
    private val cache: PunishmentCache,
    private val appConfig: AppConfig
) {

    private val logger = LoggerFactory.getLogger(ExpirationService::class.java)

    suspend fun processExpired(): Int {
        var total = 0
        while (true) {
            val expired = repository.expireDue(System.currentTimeMillis(), EXPIRE_BATCH_SIZE)
            if (expired.isEmpty()) {
                break
            }

            total += expired.size
            expired.forEach { record ->
                events.publish(
                    PunishmentEvent.PunishmentExpired(
                        metadata = EventMetadata(sourceServer = appConfig.instanceId),
                        punishmentId = record.id
                    )
                )
            }

            if (expired.size < EXPIRE_BATCH_SIZE) {
                break
            }
        }

        if (total > 0) {
            cache.invalidateAll()
            logger.info("Expired {} punishments", total)
        }
        return total
    }

    private companion object {
        const val EXPIRE_BATCH_SIZE = 100
    }
}
