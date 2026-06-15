package punishments.service.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.XAddArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.resource.DefaultClientResources
import kotlinx.coroutines.future.await
import punishments.service.config.RedisConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thin async Redis facade used by caches, event streams and leader election.
 *
 * The service keeps a small round-robin connection pool for normal commands and a
 * dedicated pub/sub connection so invalidation messages cannot block cache reads.
 */
class RedisCache(config: RedisConfig) {

    private val redisPassword = config.password
    private val clientResources = DefaultClientResources.builder()
        .ioThreadPoolSize(4)
        .computationThreadPoolSize(4)
        .build()

    private val client: RedisClient = RedisClient.create(clientResources, config.node)
    private val poolSize = System.getenv("REDIS_CONNECTION_POOL_SIZE")?.toIntOrNull()?.coerceAtLeast(1) ?: 4
    private val connections: List<StatefulRedisConnection<String, String>> = (0 until poolSize).map {
        client.connect().also { connection ->
            config.password?.takeIf(String::isNotBlank)?.let(connection.sync()::auth)
            connection.setAutoFlushCommands(true)
        }
    }
    private val commands: List<RedisAsyncCommands<String, String>> = connections.map { it.async() }
    private val counter = AtomicInteger(0)

    private fun nextCommands(): RedisAsyncCommands<String, String> {
        val index = (counter.getAndIncrement() and Int.MAX_VALUE) % poolSize
        return commands[index]
    }

    suspend fun get(key: String): String? = nextCommands().get(key).await()

    suspend fun getLong(key: String): Long? = get(key)?.toLongOrNull()

    suspend fun set(key: String, value: String, ttlSeconds: Long) {
        nextCommands().setex(key, ttlSeconds, value).await()
    }

    suspend fun set(key: String, value: String) {
        nextCommands().set(key, value).await()
    }

    suspend fun setNx(key: String, value: String, ttlSeconds: Long): Boolean {
        val result = nextCommands().set(key, value, SetArgs.Builder.nx().ex(ttlSeconds)).await()
        return result == "OK"
    }

    suspend fun expire(key: String, ttlSeconds: Long) {
        nextCommands().expire(key, ttlSeconds).await()
    }

    suspend fun incr(key: String): Long = nextCommands().incr(key).await()

    suspend fun delete(key: String) {
        nextCommands().del(key).await()
    }

    suspend fun deleteBatch(keys: List<String>) {
        if (keys.isEmpty()) {
            return
        }
        nextCommands().del(*keys.toTypedArray()).await()
    }

    suspend fun addStreamEntry(stream: String, fields: Map<String, String>, maxLength: Long) {
        nextCommands().xadd(
            stream,
            XAddArgs().maxlen(maxLength).approximateTrimming(),
            fields
        ).await()
    }

    suspend fun publish(channel: String, message: String) {
        nextCommands().publish(channel, message).await()
    }

    suspend fun eval(script: String, outputType: ScriptOutputType, keys: Array<String>, vararg args: String): Any? {
        return nextCommands().eval<Any>(script, outputType, keys, *args).await()
    }

    fun createPubSubConnection(): StatefulRedisPubSubConnection<String, String> {
        val connection = client.connectPubSub()
        redisPassword?.takeIf(String::isNotBlank)?.let(connection.sync()::auth)
        return connection
    }

    fun getCommands(): RedisAsyncCommands<String, String> = commands.first()

    fun getConnection(): StatefulRedisConnection<String, String> = connections.first()

    fun close() {
        connections.forEach { it.close() }
        client.shutdown(0, 2, TimeUnit.SECONDS)
        clientResources.shutdown()
    }

}
