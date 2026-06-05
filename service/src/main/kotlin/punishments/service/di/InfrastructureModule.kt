package punishments.service.di

import org.koin.dsl.module
import punishments.service.cache.PunishmentCache
import punishments.service.cache.RedisCache
import punishments.service.messaging.RedisEventPublisher
import punishments.service.persistence.DatabaseManager

val infrastructureModule = module {
    single { DatabaseManager(get()) }
    single { RedisCache(get()) }
    single { PunishmentCache(get(), get()) }
    single { RedisEventPublisher(get(), get()) }
}
