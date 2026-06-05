package punishments.service.persistence.migration

import org.flywaydb.core.Flyway
import punishments.service.config.DatabaseConfig

object FlywayMigrator {

    fun migrate(config: DatabaseConfig) {
        Flyway.configure()
            .dataSource(config.url, config.username, config.password)
            .locations("classpath:db.migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()
    }
}
