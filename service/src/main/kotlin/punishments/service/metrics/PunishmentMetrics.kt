package punishments.service.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class PunishmentMetrics(private val registry: MeterRegistry) {

    val punishmentsCreated: Counter = counter("punishments.created", "Total punishments created")
    val punishmentsRevoked: Counter = counter("punishments.revoked", "Total punishments revoked")
    val punishmentsExpired: Counter = counter("punishments.expired", "Total punishments expired")
    val enforcementChecks: Counter = counter("punishments.enforcement.checks", "Strict enforcement checks")
    val enforcementRestricted: Counter = counter("punishments.enforcement.restricted", "Strict checks returning active restrictions")

    val cacheHits: Counter = counter("punishments.cache.hits", "Total cache hits")
    val cacheMisses: Counter = counter("punishments.cache.misses", "Total cache misses")
    val cacheL1Hits: Counter = counter("punishments.cache.l1.hits", "L1 cache hits")
    val cacheL1Misses: Counter = counter("punishments.cache.l1.misses", "L1 cache misses")
    val cacheL2Hits: Counter = counter("punishments.cache.l2.hits", "L2 Redis cache hits")
    val cacheL2Misses: Counter = counter("punishments.cache.l2.misses", "L2 Redis cache misses")
    val cacheL3Hits: Counter = counter("punishments.cache.l3.hits", "DB fallback hits")
    val cacheL3Misses: Counter = counter("punishments.cache.l3.misses", "DB fallback misses")
    val cacheRevisionMismatches: Counter = counter("punishments.cache.revision.mismatches", "Strict cache revision mismatches")
    val cacheInvalidations: Counter = counter("punishments.cache.invalidations", "Cache invalidations")
    val redisErrors: Counter = counter("punishments.redis.errors", "Redis operation failures")
    val pubSubReconnects: Counter = counter("punishments.cache.pubsub.reconnects", "Cache pub/sub reconnects")

    val redisLatency: Timer = timer("punishments.cache.redis.latency", "Redis cache operation latency")
    val dbQueryDuration: Timer = timer("punishments.db.query.duration", "Database query latency")
    val invalidationLatency: Timer = timer("punishments.cache.invalidation.latency", "Cache invalidation latency")
    val expirationDuration: Timer = timer("punishments.expiration.duration", "Expiration batch duration")

    private val strictDetailsSize = AtomicLong()
    private val strictTargetActiveSize = AtomicLong()
    private val listingsSize = AtomicLong()
    private val searchSize = AtomicLong()
    private val catalogSize = AtomicLong()
    private val activeRestrictions = AtomicLong()
    private val cacheGeneration = AtomicLong()

    init {
        registry.gauge("punishments.cache.l1.strict_details.size", strictDetailsSize) { it.toDouble() }
        registry.gauge("punishments.cache.l1.strict_target_active.size", strictTargetActiveSize) { it.toDouble() }
        registry.gauge("punishments.cache.l1.listings.size", listingsSize) { it.toDouble() }
        registry.gauge("punishments.cache.l1.search.size", searchSize) { it.toDouble() }
        registry.gauge("punishments.cache.l1.bounded_listings.size", listingsSize) { it.toDouble() }
        registry.gauge("punishments.cache.l1.bounded_search.size", searchSize) { it.toDouble() }
        registry.gauge("punishments.cache.l1.catalog.size", catalogSize) { it.toDouble() }
        registry.gauge("punishments.cache.generation", cacheGeneration) { it.toDouble() }
        registry.gauge("punishments.active_restrictions", activeRestrictions) { it.toDouble() }
    }

    fun setL1Sizes(
        strictDetails: Long,
        strictTargetActive: Long,
        listings: Long,
        search: Long,
        catalog: Long
    ) {
        strictDetailsSize.set(strictDetails)
        strictTargetActiveSize.set(strictTargetActive)
        listingsSize.set(listings)
        searchSize.set(search)
        catalogSize.set(catalog)
    }

    fun setActiveRestrictions(count: Long) {
        activeRestrictions.set(count)
    }

    fun incrementCacheGeneration() {
        cacheGeneration.incrementAndGet()
    }

    fun cacheTierHit(family: CacheFamily, tier: CacheTier) {
        if (tier != CacheTier.L3) {
            cacheHits.increment()
        }
        when (tier) {
            CacheTier.L1 -> cacheL1Hits.increment()
            CacheTier.L2 -> cacheL2Hits.increment()
            CacheTier.L3 -> cacheL3Hits.increment()
        }
        cacheFamilyHit(family, tier)
    }

    fun cacheTierMiss(family: CacheFamily, tier: CacheTier) {
        if (tier == CacheTier.L2) {
            cacheMisses.increment()
        }
        when (tier) {
            CacheTier.L1 -> cacheL1Misses.increment()
            CacheTier.L2 -> cacheL2Misses.increment()
            CacheTier.L3 -> cacheL3Misses.increment()
        }
        cacheFamilyMiss(family, tier)
    }

    fun cacheFamilyHit(family: CacheFamily, tier: CacheTier) {
        registry.counter(
            "punishments.cache.family.hits",
            "family", family.metricName,
            "tier", tier.metricName
        ).increment()
    }

    fun cacheFamilyMiss(family: CacheFamily, tier: CacheTier) {
        registry.counter(
            "punishments.cache.family.misses",
            "family", family.metricName,
            "tier", tier.metricName
        ).increment()
    }

    private fun counter(name: String, description: String): Counter {
        return Counter.builder(name).description(description).register(registry)
    }

    private fun timer(name: String, description: String): Timer {
        return Timer.builder(name)
            .description(description)
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofNanos(100_000))
            .maximumExpectedValue(Duration.ofSeconds(5))
            .register(registry)
    }
}

enum class CacheFamily(val metricName: String) {
    STRICT_DETAILS("strict_details"),
    STRICT_TARGET_ACTIVE("strict_target_active"),
    LISTINGS("listings"),
    SEARCH("search"),
    CATALOG("catalog")
}

enum class CacheTier(val metricName: String) {
    L1("l1"),
    L2("l2"),
    L3("l3")
}
