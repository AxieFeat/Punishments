package punishments

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory
import punishments.common.serialization.CommonSerializersModule
import punishments.service.cache.RedisCache
import punishments.service.config.AppConfig
import punishments.service.config.DatabaseConfig
import punishments.service.di.configModule
import punishments.service.di.infrastructureModule
import punishments.service.di.repositoryModule
import punishments.service.di.serviceModule
import punishments.service.grpc.PunishmentGrpcServer
import punishments.service.persistence.DatabaseManager
import punishments.service.persistence.migration.FlywayMigrator
import punishments.service.scheduling.ExpirationScheduler
import punishments.service.web.routes.healthRoutes

private val logger = LoggerFactory.getLogger("PunishmentsApplication")

fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    startKoin {
        modules(configModule, infrastructureModule, repositoryModule, serviceModule)
    }

    val koin = getKoin()
    val appConfig = koin.get<AppConfig>()
    val databaseConfig = koin.get<DatabaseConfig>()

    logger.info("Starting Punishments service instance {}", appConfig.instanceId)
    FlywayMigrator.migrate(databaseConfig)

    val grpcServer = koin.get<PunishmentGrpcServer>()
    grpcServer.start()

    val expirationScheduler = koin.get<ExpirationScheduler>()
    expirationScheduler.start(scope)

    val httpServer = embeddedServer(Netty, port = appConfig.httpPort) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    serializersModule = CommonSerializersModule
                }
            )
        }
        install(CallLogging)
        routing {
            healthRoutes()
        }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            logger.info("Shutting down Punishments service instance {}", appConfig.instanceId)
            expirationScheduler.stop()
            grpcServer.stop()
            httpServer.stop(SHUTDOWN_GRACE_MILLIS, SHUTDOWN_TIMEOUT_MILLIS)
            koin.get<RedisCache>().close()
            koin.get<DatabaseManager>().close()
            scope.cancel()
            stopKoin()
        }
    )

    logger.info(
        "Punishments service started. Instance: {}, HTTP: {}, gRPC: {}",
        appConfig.instanceId,
        appConfig.httpPort,
        appConfig.grpcPort
    )
    httpServer.start(wait = true)
}

private const val SHUTDOWN_GRACE_MILLIS = 1_000L
private const val SHUTDOWN_TIMEOUT_MILLIS = 5_000L
