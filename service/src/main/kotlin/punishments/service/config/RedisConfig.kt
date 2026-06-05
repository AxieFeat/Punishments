package punishments.service.config

data class RedisConfig(
    val nodes: List<String> = System.getenv("REDIS_NODES")
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?: applicationConfig.stringList("redis.nodes", listOf("redis://localhost:6379")),
    val password: String? = System.getenv("REDIS_PASSWORD")
)
