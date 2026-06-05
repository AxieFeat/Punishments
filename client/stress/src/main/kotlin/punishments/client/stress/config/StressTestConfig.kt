package punishments.client.stress.config

import punishments.client.stress.simulation.BehaviorType

data class StressTestConfig(
    val serviceAddresses: List<String> = listOf("localhost:9090"),
    val serviceToken: String = "",
    val totalPlayers: Int = 50,
    val spawnRatePerSecond: Int = 5,
    val durationSeconds: Long = 300,
    val metricsIntervalSeconds: Long = 10,
    val behaviorDistribution: BehaviorDistribution = BehaviorDistribution(),
    val grpcTimeoutMs: Long = 5_000,
    val grpcRetryAttempts: Int = 3,
    val grpcKeepAliveSeconds: Long = 60,
    val channelsPerAddress: Int = 1,
    val maxBrowsePage: Int = 10,
    val pageSize: Int = 36,
    val simulatedServers: Int = 3
)

data class BehaviorDistribution(
    val observer: Double = 0.35,
    val moderator: Double = 0.30,
    val auditor: Double = 0.20,
    val chaotic: Double = 0.10,
    val afk: Double = 0.05
) {
    init {
        val total = observer + moderator + auditor + chaotic + afk
        require(total > 0.0) { "Behavior distribution must have a positive total weight" }
    }

    fun pick(sample: Double): BehaviorType {
        var remaining = sample.coerceIn(0.0, 0.999999999)
        val total = observer + moderator + auditor + chaotic + afk
        val weights = listOf(
            BehaviorType.OBSERVER to observer / total,
            BehaviorType.MODERATOR to moderator / total,
            BehaviorType.AUDITOR to auditor / total,
            BehaviorType.CHAOTIC to chaotic / total,
            BehaviorType.AFK to afk / total
        )

        for ((behavior, weight) in weights) {
            remaining -= weight
            if (remaining < 0.0) {
                return behavior
            }
        }

        return BehaviorType.OBSERVER
    }
}
