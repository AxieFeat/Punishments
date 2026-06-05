package punishments.service.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.getKoin
import punishments.service.config.AppConfig

fun Routing.healthRoutes() {
    val appConfig = getKoin().get<AppConfig>()

    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            HealthResponse(
                status = "ok",
                instanceId = appConfig.instanceId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    get("/ready") {
        call.respond(
            HttpStatusCode.OK,
            ReadyResponse(status = "ready", instanceId = appConfig.instanceId)
        )
    }
}

@Serializable
private data class HealthResponse(
    val status: String,
    val instanceId: String,
    val timestamp: Long
)

@Serializable
private data class ReadyResponse(
    val status: String,
    val instanceId: String
)
