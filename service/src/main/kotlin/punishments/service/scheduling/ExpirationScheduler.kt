package punishments.service.scheduling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import punishments.service.config.PunishmentServiceConfig
import punishments.service.domain.service.ExpirationService

class ExpirationScheduler(
    private val expirationService: ExpirationService,
    private val config: PunishmentServiceConfig
) {

    private val logger = LoggerFactory.getLogger(ExpirationScheduler::class.java)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            logger.info("ExpirationScheduler started")
            while (isActive) {
                try {
                    expirationService.processExpired()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Error while processing expired punishments", e)
                }
                delay(config.expirationIntervalMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
