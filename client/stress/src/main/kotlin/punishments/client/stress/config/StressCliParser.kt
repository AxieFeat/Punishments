package punishments.client.stress.config

sealed interface StressCliParseResult {
    data object Help : StressCliParseResult
    data class Run(val config: StressTestConfig) : StressCliParseResult
}

object StressCliParser {

    fun parse(args: Array<String>): StressCliParseResult {
        var hosts = listOf("localhost:9090")
        var token = ""
        var players = 500
        var spawnRate = 500
        var metricsInterval = 10L
        var timeoutMs = 3_000L
        var retries = 3
        var keepAliveSeconds = 60L
        var channelsPerAddress = 1
        var metricsUrls = listOf("http://localhost:8080/metrics")
        var maxBrowsePage = 12
        var pageSize = 36
        var logicalServers = 3
        var profile = WorkloadProfile.MODERATION
        var warmUpSeconds: Long? = null
        var rampUpSeconds: Long? = null
        var steadySeconds: Long? = null
        var spikeSeconds: Long? = null
        var cooldownSeconds: Long? = null
        var legacyDurationSeconds: Long? = null
        var outputDirectory = "build/reports/stress"
        var hotTargetPoolSize = 24
        var hotTargetShare = 0.30
        var pageDistributionLambda = 1.10

        var index = 0
        while (index < args.size) {
            when (val option = args[index]) {
                "--host" -> hosts = listOf(nextValue(args, ++index, option))
                "--hosts" -> {
                    hosts = nextValue(args, ++index, option)
                        .split(",")
                        .map(String::trim)
                        .filter(String::isNotBlank)
                }

                "--token" -> token = nextValue(args, ++index, option)
                "--players" -> players = nextValue(args, ++index, option).toIntStrict(option)
                "--spawn-rate" -> spawnRate = nextValue(args, ++index, option).toIntStrict(option)
                "--metrics-interval" -> metricsInterval = nextValue(args, ++index, option).toLongStrict(option)
                "--timeout" -> timeoutMs = nextValue(args, ++index, option).toLongStrict(option)
                "--retries" -> retries = nextValue(args, ++index, option).toIntStrict(option)
                "--keep-alive" -> keepAliveSeconds = nextValue(args, ++index, option).toLongStrict(option)
                "--channels-per-address" -> channelsPerAddress = nextValue(args, ++index, option).toIntStrict(option)
                "--metrics-url" -> metricsUrls = listOf(nextValue(args, ++index, option))
                "--metrics-urls" -> {
                    metricsUrls = nextValue(args, ++index, option)
                        .split(",")
                        .map(String::trim)
                        .filter(String::isNotBlank)
                }
                "--max-page" -> maxBrowsePage = nextValue(args, ++index, option).toIntStrict(option)
                "--page-size" -> pageSize = nextValue(args, ++index, option).toIntStrict(option)
                "--servers" -> logicalServers = nextValue(args, ++index, option).toIntStrict(option)
                "--profile", "--preset" -> profile = WorkloadProfile.parse(nextValue(args, ++index, option))
                "--duration" -> legacyDurationSeconds = nextValue(args, ++index, option).toLongStrict(option)
                "--warm-up" -> warmUpSeconds = nextValue(args, ++index, option).toLongStrict(option)
                "--ramp-up" -> rampUpSeconds = nextValue(args, ++index, option).toLongStrict(option)
                "--steady" -> steadySeconds = nextValue(args, ++index, option).toLongStrict(option)
                "--spike" -> spikeSeconds = nextValue(args, ++index, option).toLongStrict(option)
                "--cooldown" -> cooldownSeconds = nextValue(args, ++index, option).toLongStrict(option)
                "--output-dir" -> outputDirectory = nextValue(args, ++index, option)
                "--hot-targets" -> hotTargetPoolSize = nextValue(args, ++index, option).toIntStrict(option)
                "--hot-ratio" -> hotTargetShare = nextValue(args, ++index, option).toDoubleStrict(option)
                "--page-lambda" -> pageDistributionLambda = nextValue(args, ++index, option).toDoubleStrict(option)
                "--help", "-h" -> return StressCliParseResult.Help
                else -> throw IllegalArgumentException("Unknown option: $option")
            }
            index++
        }

        val scenario = ScenarioPlan.default(
            warmUpSeconds = warmUpSeconds ?: DEFAULT_WARM_UP_SECONDS,
            rampUpSeconds = rampUpSeconds ?: DEFAULT_RAMP_UP_SECONDS,
            steadySeconds = steadySeconds ?: legacyDurationSeconds ?: DEFAULT_STEADY_SECONDS,
            spikeSeconds = spikeSeconds ?: DEFAULT_SPIKE_SECONDS,
            cooldownSeconds = cooldownSeconds ?: DEFAULT_COOLDOWN_SECONDS
        )

        return StressCliParseResult.Run(
            StressTestConfig(
                serviceAddresses = hosts,
                serviceToken = token,
                totalPlayers = players,
                spawnRatePerSecond = spawnRate,
                metricsIntervalSeconds = metricsInterval,
                requestTimeoutMs = timeoutMs,
                requestRetryAttempts = retries,
                grpcKeepAliveSeconds = keepAliveSeconds,
                channelsPerAddress = channelsPerAddress,
                metricsUrls = metricsUrls,
                maxBrowsePage = maxBrowsePage,
                pageSize = pageSize,
                logicalServers = logicalServers,
                profile = profile,
                scenario = scenario,
                outputDirectory = outputDirectory,
                hotTargetPoolSize = hotTargetPoolSize,
                hotTargetShare = hotTargetShare,
                pageDistributionLambda = pageDistributionLambda
            )
        )
    }

    fun usage(): String {
        return """
            Punishment Service Stress Test

            Usage:
              java -jar client-stress.jar [options]

            Core options:
              --host HOST:PORT              Single gRPC service address
              --hosts H1,H2,...             Multiple gRPC service addresses
              --token TOKEN                 Auth token for the service
              --players N                   Steady-state virtual clients
              --spawn-rate N                Max client activation rate per second
              --profile NAME                observer|moderation|audit|revoke-heavy|chaos|enforcement-heavy
              --servers N                   Logical game-server client groups

            Scenario phases:
              --warm-up N                   Warm-up duration in seconds
              --ramp-up N                   Ramp-up duration in seconds
              --steady N                    Steady-state duration in seconds
              --spike N                     Spike duration in seconds
              --cooldown N                  Cooldown duration in seconds
              --duration N                  Legacy alias for --steady

            Request tuning:
              --timeout N                   Per-request deadline in ms
              --retries N                   Retry attempts per operation
              --keep-alive N                gRPC keep-alive in seconds
              --channels-per-address N      Channels per service address
              --metrics-url URL             Single Prometheus metrics URL
              --metrics-urls U1,U2,...      Multiple Prometheus metrics URLs
              --max-page N                  Max page number for browse/search
              --page-size N                 Page size for list endpoints
              --page-lambda N               Exponential page-distribution lambda
              --hot-targets N               Hot-target pool size per logical server
              --hot-ratio N                 Probability of choosing hot targets

            Output:
              --metrics-interval N          Console metrics interval in seconds
              --output-dir PATH             Base directory for results.jsonl / summary.* / report.html
        """.trimIndent()
    }

    private fun nextValue(args: Array<String>, index: Int, option: String): String {
        if (index >= args.size) {
            throw IllegalArgumentException("Missing value for $option")
        }
        return args[index]
    }

    private fun String.toIntStrict(option: String): Int {
        return toIntOrNull() ?: throw IllegalArgumentException("Expected integer value for $option, got '$this'")
    }

    private fun String.toLongStrict(option: String): Long {
        return toLongOrNull() ?: throw IllegalArgumentException("Expected long value for $option, got '$this'")
    }

    private fun String.toDoubleStrict(option: String): Double {
        return toDoubleOrNull() ?: throw IllegalArgumentException("Expected numeric value for $option, got '$this'")
    }

    private const val DEFAULT_WARM_UP_SECONDS = 30L
    private const val DEFAULT_RAMP_UP_SECONDS = 45L
    private const val DEFAULT_STEADY_SECONDS = 300L
    private const val DEFAULT_SPIKE_SECONDS = 45L
    private const val DEFAULT_COOLDOWN_SECONDS = 30L
}
