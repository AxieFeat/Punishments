package punishments.client.stress.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.slf4j.LoggerFactory
import punishments.client.common.config.ClientConfig
import punishments.client.common.network.GrpcPunishmentClient
import punishments.client.stress.config.StressTestConfig
import punishments.client.stress.metrics.MetricsCollector
import punishments.client.stress.metrics.PhaseExecutionSummary
import punishments.client.stress.metrics.RunArtifacts
import punishments.client.stress.metrics.ServerMetricsSnapshot
import punishments.client.stress.report.StressReportWriter
import punishments.common.dto.request.GetCatalogRequest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.ceil

class SimulationEngine(
    private val config: StressTestConfig
) {

    private val logger = LoggerFactory.getLogger(SimulationEngine::class.java)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(METRICS_TIMEOUT_SECONDS))
        .build()

    suspend fun run(): RunArtifacts {
        val runId = StressReportWriter.createRunId()
        val outputDirectory = StressReportWriter.createOutputDirectory(config.outputDirectory, runId)
        val artifacts = RunArtifacts(
            outputDirectory = outputDirectory.toAbsolutePath().toString(),
            resultsJsonl = outputDirectory.resolve("results.jsonl").toAbsolutePath().toString(),
            summaryJson = outputDirectory.resolve("summary.json").toAbsolutePath().toString(),
            summaryCsv = outputDirectory.resolve("summary.csv").toAbsolutePath().toString(),
            reportHtml = outputDirectory.resolve("report.html").toAbsolutePath().toString()
        )
        val loadState = MutableStateFlow(RuntimeLoadState())
        val sharedState = SharedSimulationState()
        val servers = createServers()

        sharedState.seedHotTargets(servers.map(ServerContext::serverId), config.hotTargetPoolSize)

        try {
            bootstrapCatalog(servers.first(), sharedState)
            MetricsCollector(runId = runId, outputDirectory = outputDirectory).use { metrics ->
                coroutineScope {
                    val workers = launchWorkers(runId, sharedState, metrics, loadState, servers)
                    val metricsJob = launchMetricsLoop(metrics, loadState)
                    val phaseSummaries = runScenario(loadState)
                    loadState.value = loadState.value.copy(
                        activePlayers = 0,
                        desiredPlayers = 0,
                        currentSpawnRate = 0,
                        completed = true
                    )
                    workers.joinAll()
                    metricsJob.cancel()
                    metricsJob.join()

                    val summary = metrics.buildSummary(
                        config = config,
                        phases = phaseSummaries,
                        artifacts = artifacts
                    )
                    StressReportWriter.writeSummary(outputDirectory, summary)
                }
            }
        } finally {
            servers.forEach(ServerContext::close)
        }

        return artifacts
    }

    private suspend fun bootstrapCatalog(server: ServerContext, sharedState: SharedSimulationState) {
        when (val execution = server.api.execute { getCatalog(GetCatalogRequest()) }) {
            is ApiExecution.Success -> {
                sharedState.catalog = execution.value.catalog
                logger.info(
                    "Loaded punishment catalog: {} reasons, {} capabilities",
                    execution.value.catalog.reasons.size,
                    execution.value.catalog.capabilities.size
                )
            }

            is ApiExecution.Failure -> {
                logger.warn("Failed to preload punishment catalog: {}", execution.errorCode, execution.throwable)
            }
        }
    }

    private fun CoroutineScope.launchMetricsLoop(
        metrics: MetricsCollector,
        loadState: MutableStateFlow<RuntimeLoadState>
    ): Job {
        return launch {
            while (!loadState.value.completed) {
                delay(config.metricsIntervalSeconds * 1_000)
                val snapshot = metrics.snapshot(
                    phase = loadState.value.phase,
                    activePlayers = loadState.value.activePlayers
                )
                logger.info("\n{}", formatMetricsSnapshot(snapshot))
                scrapeServerMetrics(metrics, loadState.value, snapshot.elapsedSeconds)
            }
        }
    }

    private suspend fun scrapeServerMetrics(
        metrics: MetricsCollector,
        state: RuntimeLoadState,
        elapsedSeconds: Double
    ) {
        config.metricsUrls.forEach { url ->
            val snapshot = runCatching {
                val body = withContext(Dispatchers.IO) {
                    val request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(METRICS_TIMEOUT_SECONDS))
                        .GET()
                        .build()
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
                }
                val values = parsePrometheusValues(body)
                val l1Hits = metric(values, "punishments_cache_l1_hits_total", "punishments_cache_l1_hits")
                val l1Misses = metric(values, "punishments_cache_l1_misses_total", "punishments_cache_l1_misses")
                val l2Hits = metric(values, "punishments_cache_l2_hits_total", "punishments_cache_l2_hits")
                val l2Misses = metric(values, "punishments_cache_l2_misses_total", "punishments_cache_l2_misses")
                ServerMetricsSnapshot(
                    timestamp = Instant.now().toString(),
                    phase = state.phase,
                    elapsedSeconds = elapsedSeconds,
                    url = url,
                    cacheHitRate = cacheRequestHitRate(l1Hits, l2Hits, l2Misses),
                    l1HitRate = ratio(l1Hits, l1Misses),
                    l2HitRate = ratio(l2Hits, l2Misses),
                    cacheGeneration = metric(values, "punishments_cache_generation"),
                    dbFallbacks = metric(values, "punishments_cache_l3_hits_total", "punishments_cache_l3_hits"),
                    redisErrors = metric(values, "punishments_redis_errors_total", "punishments_redis_errors"),
                    grpcCalls = metric(values, "grpc_server_requests_total", "grpc_server_calls_total", "grpc_server_calls")
                )
            }.getOrElse { error ->
                logger.debug("Failed to scrape stress server metrics from {}: {}", url, error.message)
                null
            }

            if (snapshot != null) {
                metrics.recordServerSnapshot(snapshot)
                logger.info("\n{}", formatServerMetricsSnapshot(snapshot))
            }
        }
    }

    private fun formatMetricsSnapshot(snapshot: punishments.client.stress.metrics.MetricsSnapshot): String {
        val header = "operation".padEnd(26) +
            "rps".padStart(8) +
            "total".padStart(10) +
            "ok".padStart(10) +
            "fail".padStart(8) +
            "ok%".padStart(8) +
            "p50".padStart(8) +
            "p95".padStart(8) +
            "p99".padStart(8) +
            "max".padStart(8) +
            "retry".padStart(8)
        val rows = snapshot.operations.joinToString(System.lineSeparator()) { operation ->
            val rps = if (snapshot.elapsedSeconds <= 0.0) 0.0 else operation.total / snapshot.elapsedSeconds
            val successRate = if (operation.total == 0L) 0.0 else operation.ok.toDouble() / operation.total
            operation.operation.take(26).padEnd(26) +
                formatDouble(rps).padStart(8) +
                operation.total.toString().padStart(10) +
                operation.ok.toString().padStart(10) +
                operation.fail.toString().padStart(8) +
                formatPercent(successRate).padStart(8) +
                "${operation.p50Ms}ms".padStart(8) +
                "${operation.p95Ms}ms".padStart(8) +
                "${operation.p99Ms}ms".padStart(8) +
                "${operation.maxMs}ms".padStart(8) +
                operation.retries.toString().padStart(8)
        }
        return buildString {
            appendLine("Stress metrics: phase=${snapshot.phase}, active=${snapshot.activePlayers}, elapsed=${formatDouble(snapshot.elapsedSeconds)}s")
            appendLine(header)
            appendLine("-".repeat(header.length))
            if (rows.isBlank()) {
                append("(no operations yet)")
            } else {
                append(rows)
            }
        }
    }

    private fun formatServerMetricsSnapshot(snapshot: ServerMetricsSnapshot): String {
        return "Server metrics: url=${snapshot.url}, phase=${snapshot.phase}, " +
            "cache=${formatNullablePercent(snapshot.cacheHitRate)}, " +
            "l1=${formatNullablePercent(snapshot.l1HitRate)}, " +
            "l2=${formatNullablePercent(snapshot.l2HitRate)}, " +
            "generation=${snapshot.cacheGeneration?.let(::formatDouble) ?: "n/a"}, " +
            "dbFallbacks=${snapshot.dbFallbacks?.let(::formatDouble) ?: "n/a"}, " +
            "redisErrors=${snapshot.redisErrors?.let(::formatDouble) ?: "n/a"}, " +
            "grpcRequests=${snapshot.grpcCalls?.let(::formatDouble) ?: "n/a"}"
    }

    private fun formatDouble(value: Double): String {
        return String.format(Locale.ROOT, "%.2f", value)
    }

    private fun formatPercent(value: Double): String {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0)
    }

    private fun formatNullablePercent(value: Double?): String {
        return value?.let(::formatPercent) ?: "n/a"
    }

    private suspend fun runScenario(loadState: MutableStateFlow<RuntimeLoadState>): List<PhaseExecutionSummary> {
        var activePlayers = 0
        val summaries = mutableListOf<PhaseExecutionSummary>()

        for (phase in config.scenario.phases) {
            val phaseStartMs = System.currentTimeMillis()
            val phaseDurationMs = phase.durationSeconds * 1_000
            var peakActivePlayers = activePlayers
            var peakSpawnRate = 0

            while (true) {
                val elapsedMs = System.currentTimeMillis() - phaseStartMs
                val progress = if (phaseDurationMs <= 0) 1.0 else (elapsedMs.toDouble() / phaseDurationMs).coerceIn(0.0, 1.0)
                val desiredPlayers = phase.targetPlayers(config.totalPlayers, progress)
                val spawnRate = phase.targetSpawnRate(config.spawnRatePerSecond, progress)
                activePlayers = moveTowards(
                    current = activePlayers,
                    desired = desiredPlayers,
                    ratePerSecond = spawnRate,
                    tickMillis = SCENARIO_TICK_MS
                )
                peakActivePlayers = maxOf(peakActivePlayers, activePlayers)
                peakSpawnRate = maxOf(peakSpawnRate, spawnRate)
                loadState.value = RuntimeLoadState(
                    phase = phase.name.wireName,
                    activePlayers = activePlayers,
                    desiredPlayers = desiredPlayers,
                    currentSpawnRate = spawnRate,
                    completed = false
                )

                if (elapsedMs >= phaseDurationMs) {
                    break
                }
                delay(SCENARIO_TICK_MS)
            }

            summaries += PhaseExecutionSummary(
                phase = phase.name.wireName,
                durationSeconds = phase.durationSeconds,
                requestedStartPlayers = phase.targetPlayers(config.totalPlayers, 0.0),
                requestedEndPlayers = phase.targetPlayers(config.totalPlayers, 1.0),
                peakActivePlayers = peakActivePlayers,
                peakSpawnRate = peakSpawnRate
            )
        }

        return summaries
    }

    private fun CoroutineScope.launchWorkers(
        runId: String,
        sharedState: SharedSimulationState,
        metrics: MetricsCollector,
        loadState: MutableStateFlow<RuntimeLoadState>,
        servers: List<ServerContext>
    ): List<Job> {
        val pageDistribution = ExponentialPageDistribution(
            maxPage = config.maxBrowsePage,
            lambda = config.pageDistributionLambda
        )
        return List(config.maxConcurrentPlayers) { index ->
            val server = servers[index % servers.size]
            val identity = VirtualModeratorIdentity.generate(index = index, serverId = server.serverId)
            val worker = VirtualModerator(
                runId = runId,
                identity = identity,
                api = server.api,
                sharedState = sharedState,
                metrics = metrics,
                config = config,
                pageDistribution = pageDistribution
            )
            launch {
                worker.run(loadState)
            }
        }
    }

    private fun createServers(): List<ServerContext> {
        return List(config.logicalServers) { index ->
            val serverId = "stress-server-${index + 1}"
            val clientConfig = ClientConfig(
                serverId = serverId,
                serviceAddresses = config.serviceAddresses,
                serviceToken = config.serviceToken,
                grpcTimeoutMs = config.requestTimeoutMs,
                grpcRetryAttempts = 1,
                grpcKeepAliveSeconds = config.grpcKeepAliveSeconds,
                channelsPerAddress = config.channelsPerAddress
            )
            ServerContext(
                serverId = serverId,
                api = StressApiAdapter(
                    client = GrpcPunishmentClient(clientConfig, config.serviceToken),
                    retryAttempts = config.requestRetryAttempts
                )
            )
        }
    }

    private fun moveTowards(current: Int, desired: Int, ratePerSecond: Int, tickMillis: Long): Int {
        if (current == desired) {
            return current
        }
        if (ratePerSecond <= 0) {
            return current
        }
        val step = ceil(ratePerSecond * (tickMillis / 1_000.0)).toInt().coerceAtLeast(1)
        return if (current < desired) {
            (current + step).coerceAtMost(desired)
        } else {
            (current - step).coerceAtLeast(desired)
        }
    }

    private fun parsePrometheusValues(body: String): Map<String, Double> {
        val values = linkedMapOf<String, Double>()
        body.lineSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
            .forEach { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size != 2) {
                    return@forEach
                }
                val metricName = parts[0].substringBefore("{")
                val value = parts[1].substringBefore(" ").toDoubleOrNull() ?: return@forEach
                values[metricName] = (values[metricName] ?: 0.0) + value
            }
        return values
    }

    private fun metric(values: Map<String, Double>, vararg names: String): Double? {
        return names.firstNotNullOfOrNull(values::get)
    }

    private fun ratio(hits: Double?, misses: Double?): Double? {
        val hitCount = hits ?: return null
        val missCount = misses ?: 0.0
        val total = hitCount + missCount
        return if (total <= 0.0) null else hitCount / total
    }

    private fun cacheRequestHitRate(l1Hits: Double?, l2Hits: Double?, l2Misses: Double?): Double? {
        val servedFromCache = (l1Hits ?: 0.0) + (l2Hits ?: 0.0)
        val cacheMisses = l2Misses ?: 0.0
        val total = servedFromCache + cacheMisses
        return if (total <= 0.0) null else servedFromCache / total
    }

    private data class ServerContext(
        val serverId: String,
        val api: StressApiAdapter
    ) : AutoCloseable {
        override fun close() {
            api.close()
        }
    }

    private companion object {
        const val SCENARIO_TICK_MS = 1_000L
        const val METRICS_TIMEOUT_SECONDS = 2L
    }
}

data class RuntimeLoadState(
    val phase: String = "idle",
    val activePlayers: Int = 0,
    val desiredPlayers: Int = 0,
    val currentSpawnRate: Int = 0,
    val completed: Boolean = false
) {
    fun isActive(workerIndex: Int): Boolean = workerIndex < activePlayers
}
