package punishments.client.common.cache

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

class ClientSideCache(ttlSeconds: Int = 10) {

    private val cache = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(ttlSeconds.toLong(), TimeUnit.SECONDS)
        .build<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        return cache.getIfPresent(key) as? T
    }

    fun put(key: String, value: Any) {
        cache.put(key, value)
    }

    fun invalidate(key: String) {
        cache.invalidate(key)
    }

    fun invalidateAll() {
        cache.invalidateAll()
    }
}
