package punishments.service.scheduling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import punishments.service.cache.CacheKeys
import punishments.service.cache.RedisCache
import punishments.service.config.AppConfig
import punishments.service.config.PunishmentServiceConfig
import punishments.service.domain.service.ExpirationService
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs expiration batches on exactly one active replica at a time.
 *
 * The Redis lease keeps horizontally scaled service instances from competing over
 * the same ACTIVE rows while still allowing another replica to take over quickly.
 */
class ExpirationScheduler(
    private val expirationService: ExpirationService,
    private val config: PunishmentServiceConfig,
    private val redis: RedisCache,
    private val appConfig: AppConfig
) {

    private val logger = LoggerFactory.getLogger(ExpirationScheduler::class.java)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            logger.info("ExpirationScheduler started")
            while (isActive) {
                try {
                    if (tryBecomeLeader()) {
                        expirationService.processExpired()
                        renewLeadership()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Error while processing expired punishments", e)
                }
                delay(config.expirationIntervalMillis.milliseconds)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun tryBecomeLeader(): Boolean {
        return redis.setNx(CacheKeys.EXPIRATION_LEADER, appConfig.instanceId, leadershipTtlSeconds())
    }

    private suspend fun renewLeadership() {
        redis.expire(CacheKeys.EXPIRATION_LEADER, leadershipTtlSeconds())
    }

    private fun leadershipTtlSeconds(): Long {
        return ((config.expirationIntervalMillis / 1_000) + 10).coerceAtLeast(15)
    }
}
