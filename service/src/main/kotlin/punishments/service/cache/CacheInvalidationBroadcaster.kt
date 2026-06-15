package punishments.service.cache

import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import org.slf4j.LoggerFactory
import punishments.service.config.AppConfig

/**
 * Best-effort cross-replica cache invalidation channel.
 *
 * Pub/Sub is used to reduce stale windows between replicas, but strict reads are
 * still protected by Redis revision keys and DB fallback when a message is lost.
 */
class CacheInvalidationBroadcaster(
    private val redis: RedisCache,
    private val appConfig: AppConfig
) {
    private val logger = LoggerFactory.getLogger(CacheInvalidationBroadcaster::class.java)
    private var pubSubConnection: StatefulRedisPubSubConnection<String, String>? = null

    internal var onInvalidate: ((String) -> Unit)? = null
    internal var onInvalidateFamily: ((String) -> Unit)? = null

    fun start() {
        val connection = redis.createPubSubConnection()
        pubSubConnection = connection
        connection.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                if (channel != CacheKeys.PUBSUB_CACHE_INVALIDATE) {
                    return
                }
                val separator = message.indexOf('|')
                if (separator < 0) {
                    return
                }

                val origin = message.substring(0, separator)
                if (origin == appConfig.instanceId) {
                    return
                }

                val key = message.substring(separator + 1)
                if (key.endsWith("*")) {
                    onInvalidateFamily?.invoke(key.dropLast(1))
                } else {
                    onInvalidate?.invoke(key)
                }
            }
        })
        connection.async().subscribe(CacheKeys.PUBSUB_CACHE_INVALIDATE)
        logger.info("Cache invalidation broadcaster subscribed to {}", CacheKeys.PUBSUB_CACHE_INVALIDATE)
    }

    suspend fun broadcastInvalidate(key: String) {
        redis.publish(CacheKeys.PUBSUB_CACHE_INVALIDATE, "${appConfig.instanceId}|$key")
    }

    suspend fun broadcastInvalidateFamily(prefix: String) {
        broadcastInvalidate("$prefix*")
    }

    fun stop() {
        pubSubConnection?.close()
        pubSubConnection = null
    }
}
