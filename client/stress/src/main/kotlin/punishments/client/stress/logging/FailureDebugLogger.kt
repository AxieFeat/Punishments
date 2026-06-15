package punishments.client.stress.logging

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Logs each unique failure signature only once to avoid flooding the stress output.
 */
object FailureDebugLogger {

    private val logger = LoggerFactory.getLogger("punishments.client.stress.failures")
    private val seenFailures = ConcurrentHashMap<String, AtomicLong>()
    private val uuidPattern =
        Regex("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b")

    fun logFailure(
        operation: String,
        signature: String,
        message: String,
        throwable: Throwable? = null
    ) {
        val key = "$operation|${normalizeSignature(signature)}"
        val count = seenFailures.computeIfAbsent(key) { AtomicLong() }.incrementAndGet()
        if (count != 1L && count % REPEAT_LOG_INTERVAL != 0L) {
            return
        }

        if (count == 1L) {
            logger.warn("[{}] {}", operation, message)
            if (throwable != null) {
                logger.debug("[{}] stacktrace for first occurrence", operation, throwable)
            }
        } else {
            logger.warn("[{}] repeated {} times: {}", operation, count, message)
        }
    }

    fun logException(operation: String, throwable: Throwable, message: String) {
        val signature = "${throwable::class.qualifiedName}:${throwable.message.orEmpty()}"
        logFailure(operation, signature, message, throwable)
    }

    private fun normalizeSignature(signature: String): String {
        return uuidPattern.replace(signature, "<uuid>")
    }

    private const val REPEAT_LOG_INTERVAL = 100L
}
