package punishments.service.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import punishments.service.metrics.CacheFamily
import punishments.service.metrics.CacheTier
import punishments.service.metrics.PunishmentMetrics
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Two-policy cache for punishment reads.
 *
 * Strict families carry revision fingerprints and are used by enforcement/details
 * paths. Bounded families allow short stale windows for UI list/search/catalog reads
 * where availability and latency matter more than immediate read-after-write.
 */
class TieredPunishmentCache(
    private val redis: RedisCache,
    private val broadcaster: CacheInvalidationBroadcaster,
    private val json: Json,
    private val metrics: PunishmentMetrics? = null
) {
    private val logger = LoggerFactory.getLogger(TieredPunishmentCache::class.java)

    private val strictDetails = strictCache(maximumSize = 50_000)
    private val strictTargetActive = strictCache(maximumSize = 100_000)
    private val boundedListings = boundedCache(maximumSize = 50_000)
    private val boundedSearch = boundedCache(maximumSize = 25_000)
    private val catalog = boundedCache(maximumSize = 256)
    private val lastBoundedInvalidationMs = AtomicLong()

    fun start(scope: CoroutineScope) {
        broadcaster.onInvalidate = { key ->
            strictDetails.invalidate(key)
            strictTargetActive.invalidate(key)
            boundedListings.invalidate(key)
            boundedSearch.invalidate(key)
            catalog.invalidate(key)
            metrics?.incrementCacheGeneration()
        }
        broadcaster.onInvalidateFamily = { prefix ->
            invalidateLocalPrefix(strictDetails, prefix)
            invalidateLocalPrefix(strictTargetActive, prefix)
            invalidateLocalPrefix(boundedListings, prefix)
            invalidateLocalPrefix(boundedSearch, prefix)
            invalidateLocalPrefix(catalog, prefix)
            metrics?.incrementCacheGeneration()
        }
        broadcaster.start()
        scope.launch {
            while (isActive) {
                delay(SIZE_REPORT_INTERVAL_MS)
                metrics?.setL1Sizes(
                    strictDetails = strictDetails.estimatedSize(),
                    strictTargetActive = strictTargetActive.estimatedSize(),
                    boundedListings = boundedListings.estimatedSize(),
                    boundedSearch = boundedSearch.estimatedSize(),
                    catalog = catalog.estimatedSize()
                )
            }
        }
    }

    suspend fun getStrictDetails(key: String, revisionKey: String): String? {
        return getStrict(strictDetails, CacheFamily.STRICT_DETAILS, key, listOf(revisionKey))
    }

    suspend fun putStrictDetails(key: String, revisionKey: String, value: String, ttlSeconds: Long = STRICT_TTL_SECONDS) {
        putStrict(strictDetails, key, listOf(revisionKey), value, ttlSeconds)
    }

    suspend fun getStrictTargetActive(key: String, revisionKeys: List<String>): String? {
        return getStrict(strictTargetActive, CacheFamily.STRICT_TARGET_ACTIVE, key, revisionKeys)
    }

    suspend fun putStrictTargetActive(
        key: String,
        revisionKeys: List<String>,
        value: String,
        ttlSeconds: Long = STRICT_TTL_SECONDS
    ) {
        putStrict(strictTargetActive, key, revisionKeys, value, ttlSeconds)
    }

    suspend fun getListing(key: String): String? {
        return getBounded(boundedListings, CacheFamily.BOUNDED_LISTINGS, key, LISTING_STALE_WINDOW_MS)
    }

    suspend fun putListing(key: String, value: String, ttlSeconds: Long = BOUNDED_REDIS_TTL_SECONDS) {
        putBounded(boundedListings, key, value, ttlSeconds)
    }

    suspend fun getSearch(key: String): String? {
        return getBounded(boundedSearch, CacheFamily.BOUNDED_SEARCH, key, SEARCH_STALE_WINDOW_MS)
    }

    suspend fun putSearch(key: String, value: String, ttlSeconds: Long = BOUNDED_REDIS_TTL_SECONDS) {
        putBounded(boundedSearch, key, value, ttlSeconds)
    }

    suspend fun getCatalog(key: String): String? {
        return getBounded(catalog, CacheFamily.CATALOG, key, CATALOG_STALE_WINDOW_MS)
    }

    suspend fun putCatalog(key: String, value: String, ttlSeconds: Long = CATALOG_REDIS_TTL_SECONDS) {
        putBounded(catalog, key, value, ttlSeconds)
    }

    suspend fun invalidatePunishment(punishmentId: String) {
        val key = CacheKeys.punishment(punishmentId)
        val revisionKey = CacheKeys.punishmentRevision(punishmentId)
        invalidateStrictKey(key, listOf(revisionKey))
    }

    suspend fun invalidateTargets(targetRevisionKeys: List<String>) {
        if (targetRevisionKeys.isEmpty()) {
            return
        }
        val start = System.nanoTime()
        try {
            // Strict target entries carry revision fingerprints, so only the
            // affected target revisions need to move forward. Unaffected hot keys
            // stay resident and stale affected keys are rejected on their next read.
            targetRevisionKeys.forEach { revisionKey -> redis.incr(revisionKey) }
            metrics?.cacheInvalidations?.increment()
            metrics?.incrementCacheGeneration()
        } catch (e: Exception) {
            metrics?.redisErrors?.increment()
            logger.warn("Failed to invalidate target strict cache", e)
        } finally {
            metrics?.invalidationLatency?.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
        }
    }

    suspend fun invalidateBoundedReads() {
        val now = System.currentTimeMillis()
        val last = lastBoundedInvalidationMs.get()
        if (now - last < BOUNDED_INVALIDATION_INTERVAL_MS || !lastBoundedInvalidationMs.compareAndSet(last, now)) {
            return
        }

        boundedListings.invalidateAll()
        boundedSearch.invalidateAll()
        metrics?.cacheInvalidations?.increment()
        metrics?.incrementCacheGeneration()
        try {
            CacheKeys.boundedReadPrefixes().forEach { prefix ->
                broadcaster.broadcastInvalidateFamily(prefix)
            }
        } catch (e: Exception) {
            metrics?.redisErrors?.increment()
            logger.debug("Bounded cache invalidation broadcast failed", e)
        }
    }

    fun stop() {
        broadcaster.stop()
    }

    private suspend fun getStrict(
        cache: Cache<String, StrictEntry>,
        family: CacheFamily,
        key: String,
        revisionKeys: List<String>
    ): String? {
        // Strict L1 entries are accepted only when their revision fingerprint still
        // matches Redis. This prevents an isolated replica from serving stale bans.
        val expectedRevision = revisionFingerprint(revisionKeys)
        cache.getIfPresent(key)?.let { entry ->
            if (entry.revision == expectedRevision) {
                recordHit(family, CacheTier.L1)
                return entry.value
            }
            metrics?.cacheRevisionMismatches?.increment()
            cache.invalidate(key)
        }
        recordMiss(family, CacheTier.L1)

        val payload = readRedisPayload<StrictPayload>(key, family) ?: run {
            recordMiss(family, CacheTier.L2)
            return null
        }

        if (payload.revision != expectedRevision) {
            metrics?.cacheRevisionMismatches?.increment()
            recordMiss(family, CacheTier.L2)
            return null
        }

        cache.put(key, StrictEntry(payload.value, payload.revision))
        recordHit(family, CacheTier.L2)
        return payload.value
    }

    private suspend fun putStrict(
        cache: Cache<String, StrictEntry>,
        key: String,
        revisionKeys: List<String>,
        value: String,
        ttlSeconds: Long
    ) {
        val revision = revisionFingerprint(revisionKeys)
        cache.put(key, StrictEntry(value, revision))
        writeRedisPayload(key, StrictPayload(value, revision), ttlSeconds)
    }

    private suspend fun getBounded(
        cache: Cache<String, BoundedEntry>,
        family: CacheFamily,
        key: String,
        staleWindowMs: Long
    ): String? {
        // Bounded reads bridge brief mutation bursts for UI pages; once the local
        // stale window is exceeded, the request must refresh through Redis/DB.
        val now = System.currentTimeMillis()
        cache.getIfPresent(key)?.let { entry ->
            if (now - entry.cachedAtEpochMs <= staleWindowMs) {
                recordHit(family, CacheTier.L1)
                return entry.value
            }
            cache.invalidate(key)
        }
        recordMiss(family, CacheTier.L1)

        val payload = readRedisPayload<BoundedPayload>(key, family) ?: run {
            recordMiss(family, CacheTier.L2)
            return null
        }
        if (now - payload.cachedAtEpochMs > staleWindowMs) {
            recordMiss(family, CacheTier.L2)
            return null
        }

        cache.put(key, BoundedEntry(payload.value, payload.cachedAtEpochMs))
        recordHit(family, CacheTier.L2)
        return payload.value
    }

    private suspend fun putBounded(
        cache: Cache<String, BoundedEntry>,
        key: String,
        value: String,
        ttlSeconds: Long
    ) {
        val now = System.currentTimeMillis()
        cache.put(key, BoundedEntry(value, now))
        writeRedisPayload(key, BoundedPayload(value, now), ttlSeconds)
    }

    private suspend fun invalidateStrictKey(key: String, revisionKeys: List<String>) {
        val start = System.nanoTime()
        try {
            revisionKeys.forEach { revisionKey -> redis.incr(revisionKey) }
            strictDetails.invalidate(key)
            strictTargetActive.invalidate(key)
            redis.delete(key)
            broadcaster.broadcastInvalidate(key)
            metrics?.cacheInvalidations?.increment()
            metrics?.incrementCacheGeneration()
        } catch (e: Exception) {
            metrics?.redisErrors?.increment()
            logger.warn("Failed to invalidate strict cache key {}", key, e)
        } finally {
            metrics?.invalidationLatency?.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
        }
    }

    private suspend fun revisionFingerprint(revisionKeys: List<String>): String {
        if (revisionKeys.isEmpty()) {
            return "0"
        }
        val values = revisionKeys.sorted().map { key ->
            val value = try {
                redis.getLong(key) ?: 0L
            } catch (e: Exception) {
                metrics?.redisErrors?.increment()
                -1L
            }
            "$key=$value"
        }.joinToString("|")
        return CacheKeys.run { values.sha256() }
    }

    private inline fun <reified T> decodePayload(raw: String): T? {
        return try {
            json.decodeFromString<T>(raw)
        } catch (_: Exception) {
            null
        }
    }

    private suspend inline fun <reified T> readRedisPayload(key: String, family: CacheFamily): T? {
        val start = System.nanoTime()
        return try {
            redis.get(key)?.let(::decodePayload)
        } catch (e: Exception) {
            metrics?.redisErrors?.increment()
            logger.debug("Redis cache read failed for {}", key, e)
            null
        } finally {
            metrics?.redisLatency?.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
        }
    }

    private suspend inline fun <reified T> writeRedisPayload(key: String, payload: T, ttlSeconds: Long) {
        val start = System.nanoTime()
        try {
            redis.set(key, json.encodeToString(payload), ttlSeconds)
        } catch (e: Exception) {
            metrics?.redisErrors?.increment()
            logger.debug("Redis cache write failed for {}", key, e)
        } finally {
            metrics?.redisLatency?.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
        }
    }

    private fun recordHit(family: CacheFamily, tier: CacheTier) {
        metrics?.cacheTierHit(family, tier)
    }

    private fun recordMiss(family: CacheFamily, tier: CacheTier) {
        metrics?.cacheTierMiss(family, tier)
    }

    private fun <T> invalidateLocalPrefix(cache: Cache<String, T>, prefix: String) {
        val keys = cache.asMap().keys.filter { key -> key.startsWith(prefix) }
        if (keys.isNotEmpty()) {
            cache.invalidateAll(keys)
        }
    }

    private fun strictCache(maximumSize: Long): Cache<String, StrictEntry> {
        return Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterAccess(STRICT_L1_TTL_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private fun boundedCache(maximumSize: Long): Cache<String, BoundedEntry> {
        return Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(BOUNDED_L1_TTL_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private data class StrictEntry(val value: String, val revision: String)
    private data class BoundedEntry(val value: String, val cachedAtEpochMs: Long)

    @Serializable
    private data class StrictPayload(val value: String, val revision: String)

    @Serializable
    private data class BoundedPayload(val value: String, val cachedAtEpochMs: Long)

    private companion object {
        const val STRICT_TTL_SECONDS = 300L
        const val STRICT_L1_TTL_SECONDS = 120L
        const val BOUNDED_L1_TTL_SECONDS = 120L
        const val BOUNDED_REDIS_TTL_SECONDS = 600L
        const val CATALOG_REDIS_TTL_SECONDS = 1_800L
        const val LISTING_STALE_WINDOW_MS = 60_000L
        const val SEARCH_STALE_WINDOW_MS = 60_000L
        const val CATALOG_STALE_WINDOW_MS = 300_000L
        const val BOUNDED_INVALIDATION_INTERVAL_MS = 30_000L
        const val SIZE_REPORT_INTERVAL_MS = 15_000L
    }
}
