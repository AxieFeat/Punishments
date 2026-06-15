package punishments.service.web.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import punishments.service.web.plugins.appMeterRegistry

fun Route.metricsRoutes() {
    get("/metrics") {
        call.respondText(
            text = appMeterRegistry.scrape(),
            contentType = ContentType.parse("text/plain; version=0.0.4; charset=utf-8")
        )
    }
}
