package punishments.client.common.config

data class ClientConfig(
    val serverId: String = "server-1",
    val serviceAddresses: List<String> = listOf("localhost:9090"),
    val serviceToken: String = "",
    val redisNode: String = "redis://localhost:6379",
    val redisPassword: String? = null,
    val eventStreamKey: String = "punishments:events",
    val eventPollBlockMs: Long = 5_000,
    val grpcTimeoutMs: Long = 5_000,
    val grpcRetryAttempts: Int = 3,
    val grpcKeepAliveSeconds: Long = 60,
    val channelsPerAddress: Int = 1
)
