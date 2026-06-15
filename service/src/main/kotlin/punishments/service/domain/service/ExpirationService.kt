package punishments.service.domain.service

import org.slf4j.LoggerFactory
import punishments.common.event.EventMetadata
import punishments.common.event.PunishmentEvent
import punishments.common.util.TargetKeys
import punishments.service.cache.CacheKeys
import punishments.service.cache.TieredPunishmentCache
import punishments.service.config.AppConfig
import punishments.service.messaging.RedisEventPublisher
import punishments.service.metrics.PunishmentMetrics
import punishments.service.persistence.repository.PunishmentRepository
import java.util.concurrent.TimeUnit

class ExpirationService(
    private val repository: PunishmentRepository,
    private val events: RedisEventPublisher,
    private val cache: TieredPunishmentCache,
    private val appConfig: AppConfig,
    private val metrics: PunishmentMetrics? = null
) {

    private val logger = LoggerFactory.getLogger(ExpirationService::class.java)

    suspend fun processExpired(): Int {
        val started = System.nanoTime()
        var total = 0
        val affectedTargetRevisionKeys = linkedSetOf<String>()
        while (true) {
            val expired = repository.expireDue(System.currentTimeMillis(), EXPIRE_BATCH_SIZE)
            if (expired.isEmpty()) {
                break
            }

            total += expired.size
            expired.forEach { record ->
                cache.invalidatePunishment(record.id.toString())
                record.targets
                    .map { target -> CacheKeys.targetRevision(TargetKeys.normalized(target)) }
                    .forEach(affectedTargetRevisionKeys::add)
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
            cache.invalidateTargets(affectedTargetRevisionKeys.toList())
            cache.invalidateBoundedReads()
            metrics?.punishmentsExpired?.increment(total.toDouble())
            metrics?.setActiveRestrictions(repository.countActiveRestrictions(System.currentTimeMillis()))
            logger.info("Expired {} punishments", total)
        }
        metrics?.expirationDuration?.record(System.nanoTime() - started, TimeUnit.NANOSECONDS)
        return total
    }

    private companion object {
        const val EXPIRE_BATCH_SIZE = 100
    }
}
