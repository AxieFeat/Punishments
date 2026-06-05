package punishments.service.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import punishments.service.config.DatabaseConfig

class DatabaseManager(private val config: DatabaseConfig) {

    private val dataSource: HikariDataSource by lazy {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.username
                password = config.password
                driverClassName = config.driverClassName
                maximumPoolSize = config.poolSize
                minimumIdle = (config.poolSize / 2).coerceAtLeast(1)
                poolName = "punishments-primary"
                isAutoCommit = false
                connectionTimeout = 5_000
                idleTimeout = 120_000
                maxLifetime = 300_000
                addDataSourceProperty("tcpKeepAlive", "true")
            }
        )
    }

    val database: Database by lazy { Database.connect(dataSource) }

    suspend fun <T> transaction(block: suspend () -> T): T {
        return suspendTransaction(db = database) { block() }
    }

    fun close() {
        if (!dataSource.isClosed) {
            dataSource.close()
        }
    }
}
