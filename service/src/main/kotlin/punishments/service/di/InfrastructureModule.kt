package punishments.service.di

import io.micrometer.core.instrument.MeterRegistry
import org.koin.dsl.module
import punishments.service.cache.CacheInvalidationBroadcaster
import punishments.service.cache.RedisCache
import punishments.service.cache.TieredPunishmentCache
import punishments.service.messaging.RedisEventPublisher
import punishments.service.metrics.PunishmentMetrics
import punishments.service.persistence.DatabaseManager
import punishments.service.web.plugins.appMeterRegistry

val infrastructureModule = module {
    single<MeterRegistry> { appMeterRegistry }
    single { DatabaseManager(get(), get(), get()) }
    single { RedisCache(get()) }
    single { CacheInvalidationBroadcaster(get(), get()) }
    single { PunishmentMetrics(get()) }
    single { TieredPunishmentCache(get(), get(), get(), get(), get()) }
    single { RedisEventPublisher(get(), get()) }
}
