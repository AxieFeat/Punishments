package punishments.service.grpc

import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import org.slf4j.LoggerFactory
import punishments.service.config.AppConfig
import punishments.service.grpc.interceptor.AuthInterceptor
import punishments.service.grpc.interceptor.LoggingInterceptor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class PunishmentGrpcServer(
    private val appConfig: AppConfig,
    private val service: PunishmentGrpcService,
    private val authInterceptor: AuthInterceptor,
    private val loggingInterceptor: LoggingInterceptor
) {

    private val logger = LoggerFactory.getLogger(PunishmentGrpcServer::class.java)
    private var server: Server? = null
    private val executor = ThreadPoolExecutor(
        2,
        16,
        60L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(QUEUE_CAPACITY)
    )

    fun start() {
        server = NettyServerBuilder.forPort(appConfig.grpcPort)
            .addService(service)
            .intercept(loggingInterceptor)
            .intercept(authInterceptor)
            .executor(executor)
            .maxInboundMessageSize(MAX_INBOUND_MESSAGE_BYTES)
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .permitKeepAliveWithoutCalls(true)
            .build()
            .start()

        logger.info("Punishment gRPC server started on port {}", appConfig.grpcPort)
    }

    fun stop() {
        server?.let { activeServer ->
            logger.info("Punishment gRPC server stopping")
            activeServer.shutdown().awaitTermination(10, TimeUnit.SECONDS)
        }
        executor.shutdown()
    }

    private companion object {
        const val QUEUE_CAPACITY = 256
        const val MAX_INBOUND_MESSAGE_BYTES = 2 * 1024 * 1024
    }
}
