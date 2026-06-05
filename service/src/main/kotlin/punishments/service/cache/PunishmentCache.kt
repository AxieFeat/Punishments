package punishments.service.cache

import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import punishments.service.config.PunishmentServiceConfig
import java.util.concurrent.TimeUnit

class PunishmentCache(
    private val redis: RedisCache,
    private val config: PunishmentServiceConfig
) {

    private val logger = LoggerFactory.getLogger(PunishmentCache::class.java)
    private val l1 = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(config.l1CacheTtlSeconds, TimeUnit.SECONDS)
        .build<String, String>()

    suspend fun get(key: String): String? {
        l1.getIfPresent(key)?.let { return it }
        return try {
            redis.get(key)?.also { l1.put(key, it) }
        } catch (e: Exception) {
            logger.debug("Redis cache read failed for {}", key, e)
            null
        }
    }

    suspend fun put(key: String, value: String, ttlSeconds: Long = config.cacheTtlSeconds) {
        l1.put(key, value)
        try {
            redis.set(key, value, ttlSeconds)
        } catch (e: Exception) {
            logger.debug("Redis cache write failed for {}", key, e)
        }
    }

    suspend fun invalidate(key: String) {
        l1.invalidate(key)
        try {
            redis.delete(key)
        } catch (e: Exception) {
            logger.debug("Redis cache delete failed for {}", key, e)
        }
    }

    suspend fun invalidateAll() {
        l1.invalidateAll()
        try {
            redis.deleteByPattern(CacheKeys.namespacePattern())
        } catch (e: Exception) {
            logger.debug("Redis cache namespace invalidation failed", e)
        }
    }
}
