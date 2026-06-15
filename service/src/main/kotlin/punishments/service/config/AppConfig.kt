package punishments.service.config

import java.util.UUID

data class AppConfig(
    val grpcPort: Int = System.getenv("GRPC_PORT")?.toIntOrNull()
        ?: applicationConfig.int("grpc.port", 9090),
    val httpPort: Int = System.getenv("HTTP_PORT")?.toIntOrNull()
        ?: applicationConfig.int("ktor.deployment.port", 8080),
    val instanceId: String = System.getenv("INSTANCE_ID")
        ?: "punishment-service-${UUID.randomUUID().toString().take(8)}",
    val serviceAuthToken: String = configuredToken("SERVICE_AUTH_TOKEN", "auth.token"),
    val grpcAuthToken: String = configuredToken("GRPC_AUTH_TOKEN", "grpc.auth-token", serviceAuthToken),
    val httpAuthToken: String = configuredToken("HTTP_AUTH_TOKEN", "http.auth-token", serviceAuthToken.ifBlank { grpcAuthToken })
)

private fun configuredToken(envName: String, configPath: String, fallback: String = ""): String {
    return System.getenv(envName)
        ?: applicationConfig.string(configPath, "").ifBlank { fallback }
}
