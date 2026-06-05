package punishments.service.di

import kotlinx.serialization.json.Json
import org.koin.dsl.module
import punishments.common.config.PunishmentCatalogLoader
import punishments.common.serialization.CommonSerializersModule
import punishments.service.config.AppConfig
import punishments.service.config.DatabaseConfig
import punishments.service.config.PunishmentServiceConfig
import punishments.service.config.RedisConfig
import punishments.service.domain.validation.PunishmentValidator

val configModule = module {
    single { AppConfig() }
    single { DatabaseConfig() }
    single { RedisConfig() }
    single { PunishmentServiceConfig() }
    single { PunishmentCatalogLoader.load() }
    single { PunishmentValidator(get()) }
    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            serializersModule = CommonSerializersModule
        }
    }
}
