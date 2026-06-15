package punishments.service.config

data class RedisConfig(
    val node: String = System.getenv("REDIS_NODES").trim().let {
        it.ifEmpty { applicationConfig.string("redis.node", "redis://localhost:6379") }
    },
    val password: String? = System.getenv("REDIS_PASSWORD")
)
