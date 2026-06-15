package punishments.service.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import io.micrometer.core.instrument.MeterRegistry
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import punishments.service.config.DatabaseConfig
import punishments.service.metrics.PunishmentMetrics
import java.util.concurrent.TimeUnit

/**
 * Owns the JDBC pool and Exposed transaction boundary for the service.
 *
 * Repository implementations should enter the database only through this class so
 * pool metrics, transaction semantics and future read/write routing stay centralized.
 */
class DatabaseManager(
    private val config: DatabaseConfig,
    meterRegistry: MeterRegistry,
    private val metrics: PunishmentMetrics
) {

    private val dataSource: HikariDataSource by lazy {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.username
                password = config.password
                driverClassName = config.driverClassName
                maximumPoolSize = config.poolSize
                minimumIdle = (config.poolSize / 2).coerceAtLeast(1)
                poolName = "punishment-service-primary"
                isAutoCommit = false
                connectionTimeout = 5_000
                idleTimeout = 120_000
                maxLifetime = 300_000
                metricsTrackerFactory = MicrometerMetricsTrackerFactory(meterRegistry)
                addDataSourceProperty("tcpKeepAlive", "true")
            }
        )
    }

    val database: Database by lazy { Database.connect(dataSource) }

    suspend fun <T> transaction(block: suspend () -> T): T {
        val startNanos = System.nanoTime()
        return try {
            suspendTransaction(db = database) { block() }
        } finally {
            metrics.dbQueryDuration.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS)
        }
    }

    fun close() {
        if (!dataSource.isClosed) {
            dataSource.close()
        }
    }
}
