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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory
import punishments.common.serialization.CommonSerializersModule
import punishments.service.cache.RedisCache
import punishments.service.cache.TieredPunishmentCache
import punishments.service.config.AppConfig
import punishments.service.config.DatabaseConfig
import punishments.service.di.configModule
import punishments.service.di.infrastructureModule
import punishments.service.di.repositoryModule
import punishments.service.di.serviceModule
import punishments.service.grpc.PunishmentGrpcServer
import punishments.service.metrics.PunishmentMetrics
import punishments.service.persistence.DatabaseManager
import punishments.service.persistence.migration.FlywayMigrator
import punishments.service.persistence.repository.PunishmentRepository
import punishments.service.scheduling.ExpirationScheduler
import punishments.service.web.plugins.configureMonitoring
import punishments.service.web.routes.healthRoutes
import punishments.service.web.routes.metricsRoutes
import punishments.service.web.routes.punishmentHttpRoutes
import punishments.common.protocol.PunishmentAPI

private val logger = LoggerFactory.getLogger("PunishmentServiceApplication")

fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Koin owns long-lived infrastructure objects; startup keeps construction
    // separate from side effects so migrations can run before any server accepts traffic.
    startKoin {
        modules(configModule, infrastructureModule, repositoryModule, serviceModule)
    }

    val koin = getKoin()
    val appConfig = koin.get<AppConfig>()
    val databaseConfig = koin.get<DatabaseConfig>()
    val punishmentApi = koin.get<PunishmentAPI>()
    val metrics = koin.get<PunishmentMetrics>()
    val repository = koin.get<PunishmentRepository>()

    logger.info("Starting punishment-service instance {}", appConfig.instanceId)
    FlywayMigrator.migrate(databaseConfig)
    runBlocking {
        metrics.setActiveRestrictions(repository.countActiveRestrictions(System.currentTimeMillis()))
    }

    val grpcServer = koin.get<PunishmentGrpcServer>()
    grpcServer.start()

    // Cross-instance cache invalidation and expiration leadership must start after
    // migrations, otherwise replicas can observe schema drift during rolling deploys.
    val tieredCache = koin.get<TieredPunishmentCache>()
    tieredCache.start(scope)

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
        configureMonitoring()
        routing {
            healthRoutes()
            metricsRoutes()
            punishmentHttpRoutes(punishmentApi, appConfig.httpAuthToken)
        }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            logger.info("Shutting down punishment-service instance {}", appConfig.instanceId)
            expirationScheduler.stop()
            tieredCache.stop()
            grpcServer.stop()
            httpServer.stop(SHUTDOWN_GRACE_MILLIS, SHUTDOWN_TIMEOUT_MILLIS)
            koin.get<RedisCache>().close()
            koin.get<DatabaseManager>().close()
            scope.cancel()
            stopKoin()
        }
    )

    logger.info(
        "punishment-service started. Instance: {}, HTTP: {}, gRPC: {}",
        appConfig.instanceId,
        appConfig.httpPort,
        appConfig.grpcPort
    )
    httpServer.start(wait = true)
}

private const val SHUTDOWN_GRACE_MILLIS = 1_000L
private const val SHUTDOWN_TIMEOUT_MILLIS = 5_000L
