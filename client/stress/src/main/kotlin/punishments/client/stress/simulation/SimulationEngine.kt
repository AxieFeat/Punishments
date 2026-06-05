package punishments.client.stress.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import punishments.client.common.network.GrpcClientConfig
import punishments.client.common.network.GrpcPunishmentClient
import punishments.client.stress.config.StressTestConfig
import punishments.client.stress.metrics.MetricsCollector
import punishments.common.dto.request.GetCatalogRequest
import kotlin.random.Random

class SimulationEngine(
    private val config: StressTestConfig
) {

    private val logger = LoggerFactory.getLogger(SimulationEngine::class.java)
    private val metrics = MetricsCollector()
    private val sharedState = SharedSimulationState()
    private val clients = List(config.simulatedServers.coerceAtLeast(1)) {
        GrpcPunishmentClient(
            config = GrpcClientConfig(
                serviceAddresses = config.serviceAddresses,
                timeoutMs = config.grpcTimeoutMs,
                retryAttempts = config.grpcRetryAttempts,
                keepAliveSeconds = config.grpcKeepAliveSeconds,
                channelsPerAddress = config.channelsPerAddress
            ),
            serviceToken = config.serviceToken
        )
    }

    suspend fun run() {
        logger.info(
            "Starting Punishments stress test: players={}, spawnRate={}/s, duration={}s, servers={}",
            config.totalPlayers,
            config.spawnRatePerSecond,
            config.durationSeconds,
            clients.size
        )

        bootstrapCatalog()
        val deadlineMs = System.currentTimeMillis() + config.durationSeconds * 1_000

        try {
            coroutineScope {
                val metricsJob = launchMetricsLoop(deadlineMs)
                val playerJobs = spawnPlayers(deadlineMs)
                playerJobs.joinAll()
                metricsJob.cancel()
                metricsJob.join()
            }
        } finally {
            metrics.printSummary()
            clients.forEach(GrpcPunishmentClient::close)
        }
    }

    private suspend fun bootstrapCatalog() {
        runCatching {
            val catalog = clients.first().getCatalog(GetCatalogRequest()).catalog
            sharedState.catalog = catalog
            logger.info("Loaded punishment catalog: {} reasons, {} capabilities", catalog.reasons.size, catalog.capabilities.size)
        }.onFailure { error ->
            logger.warn("Failed to preload punishment catalog", error)
        }
    }

    private fun CoroutineScope.launchMetricsLoop(deadlineMs: Long): Job {
        return launch {
            while (System.currentTimeMillis() < deadlineMs) {
                delay(config.metricsIntervalSeconds * 1_000)
                metrics.printSummary()
            }
        }
    }

    private suspend fun CoroutineScope.spawnPlayers(deadlineMs: Long): List<Job> {
        val jobs = mutableListOf<Job>()
        val spawnDelayMs = if (config.spawnRatePerSecond <= 0) 0L else (1_000.0 / config.spawnRatePerSecond).toLong().coerceAtLeast(1)

        repeat(config.totalPlayers) { index ->
            val client = clients[index % clients.size]
            val behavior = config.behaviorDistribution.pick(Random.nextDouble())
            val identity = VirtualModeratorIdentity.generateRandom(index + 1)
            val moderator = VirtualModerator(identity, behavior, client, sharedState, metrics, config)
            jobs += launch {
                moderator.runUntil(deadlineMs)
            }

            if (spawnDelayMs > 0 && index + 1 < config.totalPlayers) {
                delay(spawnDelayMs)
            }
        }

        return jobs
    }
}
