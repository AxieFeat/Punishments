package punishments.service.web.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

/**
 * Shared Prometheus registry for HTTP, JVM, Hikari and custom service meters.
 *
 * Keeping one registry avoids split `/metrics` output and ensures every metric has
 * the same `application=punishment-service` tag for Prometheus/Grafana filtering.
 */
val appMeterRegistry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT).apply {
    config().commonTags("application", "punishment-service")
    config().meterFilter(object : MeterFilter {
        private val histogramNames = setOf(
            "grpc.server.call.duration",
            "hikaricp.connections.acquire",
            "hikaricp.connections.creation",
            "hikaricp.connections.usage",
            "ktor.http.server.requests"
        )

        override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig {
            if (id.name !in histogramNames) {
                return config
            }

            return DistributionStatisticConfig.builder()
                .percentilesHistogram(true)
                .percentiles(0.5, 0.95, 0.99)
                .build()
                .merge(config)
        }
    })
}

fun Application.configureMonitoring() {
    install(MicrometerMetrics) {
        registry = appMeterRegistry
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ClassLoaderMetrics(),
            ProcessorMetrics(),
            UptimeMetrics()
        )
    }
}
