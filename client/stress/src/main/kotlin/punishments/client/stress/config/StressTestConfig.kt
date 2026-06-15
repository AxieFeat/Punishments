package punishments.client.stress.config

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.max

@Serializable
data class StressTestConfig(
    val serviceAddresses: List<String> = listOf("localhost:9090"),
    val serviceToken: String = "",
    val totalPlayers: Int = 200,
    val spawnRatePerSecond: Int = 20,
    val metricsIntervalSeconds: Long = 10,
    val requestTimeoutMs: Long = 3_000,
    val requestRetryAttempts: Int = 3,
    val grpcKeepAliveSeconds: Long = 60,
    val channelsPerAddress: Int = 1,
    val metricsUrls: List<String> = listOf("http://localhost:8080/metrics"),
    val maxBrowsePage: Int = 12,
    val pageSize: Int = 36,
    val logicalServers: Int = 3,
    val profile: WorkloadProfile = WorkloadProfile.MODERATION,
    val scenario: ScenarioPlan = ScenarioPlan.default(),
    val outputDirectory: String = "build/reports/stress",
    val hotTargetPoolSize: Int = 24,
    val hotTargetShare: Double = 0.30,
    val pageDistributionLambda: Double = 1.10
) {
    init {
        require(serviceAddresses.isNotEmpty()) { "At least one service address is required" }
        require(totalPlayers >= 0) { "Players must be non-negative" }
        require(spawnRatePerSecond >= 0) { "Spawn rate must be non-negative" }
        require(metricsIntervalSeconds > 0) { "Metrics interval must be positive" }
        require(requestTimeoutMs > 0) { "Request timeout must be positive" }
        require(requestRetryAttempts > 0) { "Retry attempts must be positive" }
        require(grpcKeepAliveSeconds > 0) { "Keep alive must be positive" }
        require(channelsPerAddress > 0) { "Channels per address must be positive" }
        require(metricsUrls.all(String::isNotBlank)) { "Metrics URLs must not be blank" }
        require(maxBrowsePage >= 0) { "Max page must be non-negative" }
        require(pageSize > 0) { "Page size must be positive" }
        require(logicalServers > 0) { "Logical server count must be positive" }
        require(hotTargetPoolSize > 0) { "Hot target pool size must be positive" }
        require(hotTargetShare in 0.0..1.0) { "Hot target share must be between 0.0 and 1.0" }
        require(pageDistributionLambda > 0.0) { "Page distribution lambda must be positive" }
    }

    val totalDurationSeconds: Long
        get() = scenario.totalDurationSeconds

    val maxConcurrentPlayers: Int
        get() = scenario.maxPlayers(totalPlayers)
}

@Serializable
enum class WorkloadProfile(val cliName: String) {
    OBSERVER("observer"),
    MODERATION("moderation"),
    AUDIT("audit"),
    REVOKE_HEAVY("revoke-heavy"),
    CHAOS("chaos"),
    ENFORCEMENT_HEAVY("enforcement-heavy");

    companion object {
        fun parse(raw: String): WorkloadProfile {
            return entries.firstOrNull { it.cliName == raw.lowercase() }
                ?: throw IllegalArgumentException(
                    "Unknown profile '$raw'. Expected one of: ${entries.joinToString { it.cliName }}"
                )
        }
    }
}

@Serializable
enum class ScenarioPhaseName(val wireName: String) {
    WARM_UP("warm_up"),
    RAMP_UP("ramp_up"),
    STEADY("steady"),
    SPIKE("spike"),
    COOLDOWN("cooldown")
}

@Serializable
data class ScenarioPhase(
    val name: ScenarioPhaseName,
    val durationSeconds: Long,
    val startPlayerFactor: Double,
    val endPlayerFactor: Double,
    val startSpawnRateFactor: Double,
    val endSpawnRateFactor: Double
) {
    init {
        require(durationSeconds >= 0) { "Phase duration must be non-negative" }
        require(startPlayerFactor >= 0.0) { "Start player factor must be non-negative" }
        require(endPlayerFactor >= 0.0) { "End player factor must be non-negative" }
        require(startSpawnRateFactor >= 0.0) { "Start spawn-rate factor must be non-negative" }
        require(endSpawnRateFactor >= 0.0) { "End spawn-rate factor must be non-negative" }
    }

    fun targetPlayers(basePlayers: Int, progress: Double): Int {
        return ceil(basePlayers * interpolate(startPlayerFactor, endPlayerFactor, progress)).toInt().coerceAtLeast(0)
    }

    fun targetSpawnRate(baseSpawnRate: Int, progress: Double): Int {
        return ceil(baseSpawnRate * interpolate(startSpawnRateFactor, endSpawnRateFactor, progress)).toInt()
            .coerceAtLeast(0)
    }

    fun maxPlayers(basePlayers: Int): Int {
        return max(
            ceil(basePlayers * startPlayerFactor).toInt(),
            ceil(basePlayers * endPlayerFactor).toInt()
        )
    }

    private fun interpolate(start: Double, end: Double, progress: Double): Double {
        val safeProgress = progress.coerceIn(0.0, 1.0)
        return start + ((end - start) * safeProgress)
    }
}

@Serializable
data class ScenarioPlan(
    val phases: List<ScenarioPhase>
) {
    init {
        require(phases.isNotEmpty()) { "At least one scenario phase is required" }
    }

    val totalDurationSeconds: Long
        get() = phases.sumOf(ScenarioPhase::durationSeconds)

    fun maxPlayers(basePlayers: Int): Int {
        return phases.maxOf { phase -> phase.maxPlayers(basePlayers) }
    }

    companion object {
        fun default(
            warmUpSeconds: Long = 30,
            rampUpSeconds: Long = 45,
            steadySeconds: Long = 300,
            spikeSeconds: Long = 45,
            cooldownSeconds: Long = 30
        ): ScenarioPlan {
            val phases = buildList {
                addIfDuration(
                    ScenarioPhase(
                        name = ScenarioPhaseName.WARM_UP,
                        durationSeconds = warmUpSeconds,
                        startPlayerFactor = 0.0,
                        endPlayerFactor = 0.30,
                        startSpawnRateFactor = 0.10,
                        endSpawnRateFactor = 0.50
                    )
                )
                addIfDuration(
                    ScenarioPhase(
                        name = ScenarioPhaseName.RAMP_UP,
                        durationSeconds = rampUpSeconds,
                        startPlayerFactor = 0.30,
                        endPlayerFactor = 1.00,
                        startSpawnRateFactor = 0.50,
                        endSpawnRateFactor = 1.00
                    )
                )
                addIfDuration(
                    ScenarioPhase(
                        name = ScenarioPhaseName.STEADY,
                        durationSeconds = steadySeconds,
                        startPlayerFactor = 1.00,
                        endPlayerFactor = 1.00,
                        startSpawnRateFactor = 1.00,
                        endSpawnRateFactor = 1.00
                    )
                )
                addIfDuration(
                    ScenarioPhase(
                        name = ScenarioPhaseName.SPIKE,
                        durationSeconds = spikeSeconds,
                        startPlayerFactor = 1.00,
                        endPlayerFactor = 1.35,
                        startSpawnRateFactor = 1.00,
                        endSpawnRateFactor = 1.60
                    )
                )
                addIfDuration(
                    ScenarioPhase(
                        name = ScenarioPhaseName.COOLDOWN,
                        durationSeconds = cooldownSeconds,
                        startPlayerFactor = 1.35,
                        endPlayerFactor = 0.10,
                        startSpawnRateFactor = 0.80,
                        endSpawnRateFactor = 0.20
                    )
                )
            }

            require(phases.isNotEmpty()) { "Scenario must include at least one non-zero phase" }
            return ScenarioPlan(phases)
        }

        private fun MutableList<ScenarioPhase>.addIfDuration(phase: ScenarioPhase) {
            if (phase.durationSeconds > 0) {
                add(phase)
            }
        }
    }
}
