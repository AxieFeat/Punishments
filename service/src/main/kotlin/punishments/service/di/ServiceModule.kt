package punishments.service.di

import org.koin.dsl.module
import punishments.common.protocol.PunishmentAPI
import punishments.service.domain.service.ExpirationService
import punishments.service.domain.service.PunishmentDomainService
import punishments.service.grpc.PunishmentGrpcServer
import punishments.service.grpc.PunishmentGrpcService
import punishments.service.grpc.interceptor.AuthInterceptor
import punishments.service.grpc.interceptor.LoggingInterceptor
import punishments.service.scheduling.ExpirationScheduler

val serviceModule = module {
    single { ExpirationService(get(), get(), get(), get()) }
    single { PunishmentDomainService(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<PunishmentAPI> { get<PunishmentDomainService>() }
    single { AuthInterceptor(get<punishments.service.config.AppConfig>().grpcAuthToken) }
    single { LoggingInterceptor() }
    single { PunishmentGrpcService(get()) }
    single { PunishmentGrpcServer(get(), get(), get(), get()) }
    single { ExpirationScheduler(get(), get()) }
}
