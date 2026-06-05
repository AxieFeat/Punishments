package punishments.client.stress

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import punishments.client.stress.config.BehaviorDistribution
import punishments.client.stress.config.StressTestConfig
import punishments.client.stress.simulation.SimulationEngine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("punishments.client.stress")
    val config = parseArgs(args)

    logger.info("Starting Punishments Stress Test")

    runBlocking {
        SimulationEngine(config).run()
    }
}

private fun parseArgs(args: Array<String>): StressTestConfig {
    var hosts = listOf("localhost:9090")
    var token = ""
    var players = 200
    var spawnRate = 20
    var duration = 300L
    var metricsInterval = 10L
    var timeout = 3_000L
    var retries = 2
    var keepAliveSeconds = 60L
    var channelsPerAddress = 1
    var maxBrowsePage = 10
    var pageSize = 36
    var simulatedServers = 3
    var behaviorDist = BehaviorDistribution()

    var index = 0
    while (index < args.size) {
        when (args[index]) {
            "--host" -> {
                index++
                hosts = listOf(args[index])
            }
            "--hosts" -> {
                index++
                hosts = args[index].split(",").map(String::trim).filter(String::isNotBlank)
            }
            "--token" -> {
                index++
                token = args[index]
            }
            "--players" -> {
                index++
                players = args[index].toInt()
            }
            "--spawn-rate" -> {
                index++
                spawnRate = args[index].toInt()
            }
            "--duration" -> {
                index++
                duration = args[index].toLong()
            }
            "--metrics-interval" -> {
                index++
                metricsInterval = args[index].toLong()
            }
            "--timeout" -> {
                index++
                timeout = args[index].toLong()
            }
            "--retries" -> {
                index++
                retries = args[index].toInt()
            }
            "--keep-alive" -> {
                index++
                keepAliveSeconds = args[index].toLong()
            }
            "--channels-per-address" -> {
                index++
                channelsPerAddress = args[index].toInt()
            }
            "--max-page" -> {
                index++
                maxBrowsePage = args[index].toInt()
            }
            "--page-size" -> {
                index++
                pageSize = args[index].toInt()
            }
            "--servers" -> {
                index++
                simulatedServers = args[index].toInt()
            }
            "--preset" -> {
                index++
                behaviorDist = when (args[index].lowercase()) {
                    "observer" -> BehaviorDistribution(observer = 0.60, moderator = 0.15, auditor = 0.15, chaotic = 0.05, afk = 0.05)
                    "balanced" -> BehaviorDistribution()
                    "moderation" -> BehaviorDistribution(observer = 0.10, moderator = 0.55, auditor = 0.15, chaotic = 0.15, afk = 0.05)
                    "audit" -> BehaviorDistribution(observer = 0.15, moderator = 0.10, auditor = 0.55, chaotic = 0.10, afk = 0.10)
                    "chaos" -> BehaviorDistribution(observer = 0.05, moderator = 0.25, auditor = 0.15, chaotic = 0.50, afk = 0.05)
                    else -> BehaviorDistribution()
                }
            }
            "--help", "-h" -> {
                printHelp()
                exitProcess(0)
            }
            else -> {
                System.err.println("Unknown option: ${args[index]}")
                printHelp()
                exitProcess(1)
            }
        }
        index++
    }

    return StressTestConfig(
        serviceAddresses = hosts,
        serviceToken = token,
        totalPlayers = 1000,
        spawnRatePerSecond = 500,
        durationSeconds = duration,
        metricsIntervalSeconds = metricsInterval,
        behaviorDistribution = behaviorDist,
        grpcTimeoutMs = timeout,
        grpcRetryAttempts = retries,
        grpcKeepAliveSeconds = keepAliveSeconds,
        channelsPerAddress = channelsPerAddress,
        maxBrowsePage = maxBrowsePage,
        pageSize = pageSize,
        simulatedServers = simulatedServers
    )
}

private fun printHelp() {
    println(
        """
        Punishments Stress Test

        Usage:
          java -jar client-stress.jar [options]

        Options:
          --host HOST:PORT              Single gRPC service address
          --hosts H1,H2,...             Multiple gRPC service addresses
          --token TOKEN                 Auth token for the service
          --players N                   Total virtual moderators/viewers
          --spawn-rate N                Spawn rate per second
          --duration N                  Test duration in seconds
          --metrics-interval N          Metrics print interval in seconds
          --timeout N                   Per-call gRPC deadline in ms
          --retries N                   Retry attempts for transient errors
          --keep-alive N                gRPC keep-alive in seconds
          --channels-per-address N      Channels per service address
          --max-page N                  Max page number for browse/search
          --page-size N                 Page size for list endpoints
          --servers N                   Number of simulated Minecraft servers
          --preset balanced|observer|moderation|audit|chaos
        """.trimIndent()
    )
}
