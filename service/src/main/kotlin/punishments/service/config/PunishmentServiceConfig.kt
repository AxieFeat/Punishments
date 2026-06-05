package punishments.service.config

data class PunishmentServiceConfig(
    val catalogVersion: String? = System.getenv("PUNISHMENT_CATALOG_VERSION")
        ?: applicationConfig.string("punishments.catalogVersion", "").ifBlank { null },
    val cacheTtlSeconds: Long = System.getenv("CACHE_TTL_SECONDS")?.toLongOrNull()
        ?: applicationConfig.int("punishments.cacheTtlSeconds", 300).toLong(),
    val l1CacheTtlSeconds: Long = System.getenv("L1_CACHE_TTL_SECONDS")?.toLongOrNull()
        ?: applicationConfig.int("punishments.l1CacheTtlSeconds", 5).toLong(),
    val expirationIntervalMillis: Long = System.getenv("EXPIRATION_INTERVAL_MS")?.toLongOrNull()
        ?: applicationConfig.int("punishments.expirationIntervalMillis", 30_000).toLong()
)
