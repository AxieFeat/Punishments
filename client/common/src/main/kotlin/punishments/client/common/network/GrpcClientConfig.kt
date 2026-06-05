package punishments.client.common.network

data class GrpcClientConfig(
    val serviceAddresses: List<String>,
    val timeoutMs: Long = 5_000,
    val retryAttempts: Int = 3,
    val keepAliveSeconds: Long = 60,
    val channelsPerAddress: Int = 1
)
