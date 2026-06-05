package punishments.client.stress.metrics

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

class MetricsCollector {

    private val logger = LoggerFactory.getLogger("punishments.client.stress.metrics")
    private val operationCounts = ConcurrentHashMap<String, AtomicLong>()
    private val successCounts = ConcurrentHashMap<String, AtomicLong>()
    private val failureCounts = ConcurrentHashMap<String, AtomicLong>()
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val maxLatencyMs = ConcurrentHashMap<String, AtomicLong>()
    private val latencySamples = ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>>()
    private val startTimeMs = System.currentTimeMillis()

    fun record(operation: String, success: Boolean, latencyMs: Long) {
        operationCounts.computeIfAbsent(operation) { AtomicLong() }.incrementAndGet()
        if (success) {
            successCounts.computeIfAbsent(operation) { AtomicLong() }.incrementAndGet()
        } else {
            failureCounts.computeIfAbsent(operation) { AtomicLong() }.incrementAndGet()
        }
        maxLatencyMs.computeIfAbsent(operation) { AtomicLong() }.updateAndGet { max -> maxOf(max, latencyMs) }

        val queue = latencySamples.computeIfAbsent(operation) { ConcurrentLinkedQueue() }
        queue.add(latencyMs)
        while (queue.size > MAX_SAMPLES) {
            queue.poll()
        }
    }

    fun incrementCounter(name: String, delta: Long = 1) {
        counters.computeIfAbsent(name) { AtomicLong() }.addAndGet(delta)
    }

    fun printSummary() {
        val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
        val operations = operationCounts.keys.sorted()

        logger.info("============================================================")
        logger.info("Stress metrics after {}s", "%.1f".format(elapsedSec))
        logger.info("============================================================")
        logger.info(
            "%-24s %8s %8s %8s %8s %8s %8s %8s".format(
                "Operation",
                "Total",
                "OK",
                "Fail",
                "P50",
                "P95",
                "P99",
                "Max"
            )
        )

        for (operation in operations) {
            val total = operationCounts[operation]?.get() ?: 0
            val ok = successCounts[operation]?.get() ?: 0
            val fail = failureCounts[operation]?.get() ?: 0
            val max = maxLatencyMs[operation]?.get() ?: 0
            val percentiles = computePercentiles(operation)

            logger.info(
                "%-24s %8d %8d %8d %8d %8d %8d %8d".format(
                    operation,
                    total,
                    ok,
                    fail,
                    percentiles.p50,
                    percentiles.p95,
                    percentiles.p99,
                    max
                )
            )
        }

        val totalCreatedPunishments = counters[CREATED_PUNISHMENTS_COUNTER]?.get() ?: 0L
        val createdPunishmentsPerSecond = if (elapsedSec > 0.0) totalCreatedPunishments / elapsedSec else 0.0
        logger.info("------------------------------------------------------------")
        logger.info("Created punishments total: {}", totalCreatedPunishments)
        logger.info("Created punishments per second: {}", "%.2f".format(createdPunishmentsPerSecond))
    }

    private fun computePercentiles(operation: String): Percentiles {
        val samples = latencySamples[operation]?.toList()?.sorted().orEmpty()
        if (samples.isEmpty()) {
            return Percentiles(0, 0, 0)
        }

        return Percentiles(
            p50 = samples[percentileIndex(samples.size, 50)],
            p95 = samples[percentileIndex(samples.size, 95)],
            p99 = samples[percentileIndex(samples.size, 99)]
        )
    }

    private fun percentileIndex(size: Int, percentile: Int): Int {
        return ((size - 1) * percentile / 100).coerceIn(0, size - 1)
    }

    private data class Percentiles(
        val p50: Long,
        val p95: Long,
        val p99: Long
    )

    private companion object {
        const val MAX_SAMPLES = 50_000
        const val CREATED_PUNISHMENTS_COUNTER = "created_punishments"
    }
}
