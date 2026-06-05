package punishments.client.common.config

import punishments.client.common.network.GrpcClientConfig

data class ClientConfig(
    val serverId: String = "server-1",
    val serviceAddresses: List<String> = listOf("localhost:9090"),
    val serviceToken: String = "",
    val redisNodes: List<String> = listOf("redis://localhost:6379"),
    val redisPassword: String? = null,
    val cacheTtlSeconds: Int = 10,
    val eventStreamKey: String = "punishments:events",
    val eventPollBlockMs: Long = 5_000,
    val grpcTimeoutMs: Long = 5_000,
    val grpcRetryAttempts: Int = 3,
    val grpcKeepAliveSeconds: Long = 60,
    val channelsPerAddress: Int = 1
) {
    fun toGrpcConfig(): GrpcClientConfig {
        return GrpcClientConfig(
            serviceAddresses = serviceAddresses,
            timeoutMs = grpcTimeoutMs,
            retryAttempts = grpcRetryAttempts,
            keepAliveSeconds = grpcKeepAliveSeconds,
            channelsPerAddress = channelsPerAddress
        )
    }
}
