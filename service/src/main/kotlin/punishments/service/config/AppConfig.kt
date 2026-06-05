package punishments.service.config

import java.util.UUID

data class AppConfig(
    val grpcPort: Int = System.getenv("GRPC_PORT")?.toIntOrNull()
        ?: applicationConfig.int("grpc.port", 9090),
    val httpPort: Int = System.getenv("HTTP_PORT")?.toIntOrNull()
        ?: applicationConfig.int("ktor.deployment.port", 8080),
    val instanceId: String = System.getenv("INSTANCE_ID")
        ?: "punishments-${UUID.randomUUID().toString().take(8)}",
    val grpcAuthToken: String = System.getenv("GRPC_AUTH_TOKEN").orEmpty()
)
