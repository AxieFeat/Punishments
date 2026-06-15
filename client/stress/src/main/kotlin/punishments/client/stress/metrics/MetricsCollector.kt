package punishments.client.stress.metrics

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import punishments.client.stress.config.StressTestConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

class MetricsCollector(
    private val runId: String,
    outputDirectory: Path,
    private val sampleLimit: Int = MAX_SAMPLES
) : AutoCloseable {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    private val resultsPath = outputDirectory.resolve(RESULTS_JSONL_FILE)
    private val writerLock = Any()
    private val writer = Files.newBufferedWriter(resultsPath, CREATE, TRUNCATE_EXISTING, WRITE)
    private val operationStats = ConcurrentHashMap<String, OperationAccumulator>()
    private val serverMetricSnapshots = ConcurrentLinkedQueue<ServerMetricsSnapshot>()
    private val startedAtMs = System.currentTimeMillis()

    fun record(event: OperationEvent) {
        operationStats.computeIfAbsent(event.operation) { OperationAccumulator(sampleLimit) }.record(event)
        synchronized(writerLock) {
            writer.append(json.encodeToString(event))
            writer.newLine()
        }
    }

    fun snapshot(phase: String, activePlayers: Int): MetricsSnapshot {
        return MetricsSnapshot(
            phase = phase,
            activePlayers = activePlayers,
            elapsedSeconds = ((System.currentTimeMillis() - startedAtMs) / 1_000.0),
            operations = buildOperationSummaries()
        )
    }

    fun recordServerSnapshot(snapshot: ServerMetricsSnapshot) {
        serverMetricSnapshots.add(snapshot)
    }

    fun buildSummary(
        config: StressTestConfig,
        phases: List<PhaseExecutionSummary>,
        artifacts: RunArtifacts
    ): RunSummary {
        val finishedAtMs = System.currentTimeMillis()
        val operations = buildOperationSummaries()
        return RunSummary(
            runId = runId,
            startedAt = Instant.ofEpochMilli(startedAtMs).toString(),
            finishedAt = Instant.ofEpochMilli(finishedAtMs).toString(),
            durationSeconds = ((finishedAtMs - startedAtMs) / 1_000.0),
            config = config,
            totals = TotalsSummary(
                operationCount = operations.sumOf(OperationSummary::total),
                okCount = operations.sumOf(OperationSummary::ok),
                failCount = operations.sumOf(OperationSummary::fail),
                retryCount = operations.sumOf(OperationSummary::retries)
            ),
            phases = phases,
            operations = operations,
            serverMetrics = serverMetricSnapshots.toList(),
            artifacts = artifacts
        )
    }

    override fun close() {
        synchronized(writerLock) {
            writer.flush()
            writer.close()
        }
    }

    private fun buildOperationSummaries(): List<OperationSummary> {
        return operationStats.entries
            .sortedBy { it.key }
            .map { (operation, accumulator) -> accumulator.toSummary(operation) }
    }

    private class OperationAccumulator(private val sampleLimit: Int) {
        private val total = AtomicLong()
        private val ok = AtomicLong()
        private val fail = AtomicLong()
        private val retries = AtomicLong()
        private val maxLatencyMs = AtomicLong()
        private val latencies = ConcurrentLinkedQueue<Long>()
        private val errorCodes = ConcurrentHashMap<String, AtomicLong>()

        fun record(event: OperationEvent) {
            total.incrementAndGet()
            if (event.success) {
                ok.incrementAndGet()
            } else {
                fail.incrementAndGet()
            }
            retries.addAndGet(event.retries.toLong())
            maxLatencyMs.updateAndGet { current -> maxOf(current, event.latencyMs) }
            latencies.add(event.latencyMs)
            while (latencies.size > sampleLimit) {
                latencies.poll()
            }
            event.errorCode?.let { code ->
                errorCodes.computeIfAbsent(code) { AtomicLong() }.incrementAndGet()
            }
        }

        fun toSummary(operation: String): OperationSummary {
            val samples = latencies.toList().sorted()
            return OperationSummary(
                operation = operation,
                total = total.get(),
                ok = ok.get(),
                fail = fail.get(),
                retries = retries.get(),
                p50Ms = percentile(samples, 50),
                p95Ms = percentile(samples, 95),
                p99Ms = percentile(samples, 99),
                maxMs = maxLatencyMs.get(),
                errorCodes = errorCodes.entries
                    .sortedBy { it.key }
                    .associate { (code, count) -> code to count.get() }
            )
        }

        private fun percentile(samples: List<Long>, percentile: Int): Long {
            if (samples.isEmpty()) {
                return 0
            }
            val index = ((samples.size - 1) * percentile / 100).coerceIn(0, samples.size - 1)
            return samples[index]
        }
    }

    companion object {
        const val RESULTS_JSONL_FILE = "results.jsonl"
        private const val MAX_SAMPLES = 100_000
    }
}

@Serializable
data class OperationEvent(
    val runId: String,
    val timestamp: String,
    val phase: String,
    val serverId: String,
    val clientName: String,
    val profile: String,
    val operation: String,
    val success: Boolean,
    val latencyMs: Long,
    val retries: Int,
    val errorCode: String? = null
)

@Serializable
data class MetricsSnapshot(
    val phase: String,
    val activePlayers: Int,
    val elapsedSeconds: Double,
    val operations: List<OperationSummary>
)

@Serializable
data class OperationSummary(
    val operation: String,
    val total: Long,
    val ok: Long,
    val fail: Long,
    val retries: Long,
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
    val maxMs: Long,
    val errorCodes: Map<String, Long>
)

@Serializable
data class ServerMetricsSnapshot(
    val timestamp: String,
    val phase: String,
    val elapsedSeconds: Double,
    val url: String,
    val cacheHitRate: Double? = null,
    val l1HitRate: Double? = null,
    val l2HitRate: Double? = null,
    val cacheGeneration: Double? = null,
    val dbFallbacks: Double? = null,
    val redisErrors: Double? = null,
    val grpcCalls: Double? = null
)

@Serializable
data class TotalsSummary(
    val operationCount: Long,
    val okCount: Long,
    val failCount: Long,
    val retryCount: Long
)

@Serializable
data class PhaseExecutionSummary(
    val phase: String,
    val durationSeconds: Long,
    val requestedStartPlayers: Int,
    val requestedEndPlayers: Int,
    val peakActivePlayers: Int,
    val peakSpawnRate: Int
)

@Serializable
data class RunArtifacts(
    val outputDirectory: String,
    val resultsJsonl: String,
    val summaryJson: String,
    val summaryCsv: String,
    val reportHtml: String
)

@Serializable
data class RunSummary(
    val runId: String,
    val startedAt: String,
    val finishedAt: String,
    val durationSeconds: Double,
    val config: StressTestConfig,
    val totals: TotalsSummary,
    val phases: List<PhaseExecutionSummary>,
    val operations: List<OperationSummary>,
    val serverMetrics: List<ServerMetricsSnapshot>,
    val artifacts: RunArtifacts
)
