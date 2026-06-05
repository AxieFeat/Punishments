package punishments.service.config

data class DatabaseConfig(
    val url: String = System.getenv("DB_PRIMARY_URL")
        ?: applicationConfig.string("database.primary", "jdbc:postgresql://localhost:5432/punishments"),
    val username: String = System.getenv("DB_USERNAME")
        ?: applicationConfig.string("database.username", "punishments"),
    val password: String = System.getenv("DB_PASSWORD")
        ?: applicationConfig.string("database.password", "punishments"),
    val poolSize: Int = System.getenv("DB_POOL_SIZE")?.toIntOrNull()
        ?: applicationConfig.int("database.poolSize", 10),
    val driverClassName: String = "org.postgresql.Driver"
)
