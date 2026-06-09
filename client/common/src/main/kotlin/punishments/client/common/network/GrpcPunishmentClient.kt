package punishments.client.common.network

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import punishments.client.common.cache.ClientSideCache
import punishments.client.common.config.ClientConfig
import punishments.client.common.messaging.DualEventConsumer
import punishments.client.common.messaging.EventDeduplicator
import punishments.client.common.messaging.EventHandler
import punishments.client.common.messaging.RedisEventConsumer
import punishments.client.common.network.mapper.ProtoClientMapper.toDomain
import punishments.client.common.network.mapper.ProtoClientMapper.toProto
import punishments.common.dto.request.CreatePunishmentRequest
import punishments.common.dto.request.GetCatalogRequest
import punishments.common.dto.request.GetPunishmentDetailsRequest
import punishments.common.dto.request.GetPunishmentsRequest
import punishments.common.dto.request.GetTargetPunishmentsRequest
import punishments.common.dto.request.RevokePunishmentRequest
import punishments.common.dto.request.SearchPunishmentsRequest
import punishments.common.dto.response.CreatePunishmentResult
import punishments.common.dto.response.PaginatedResponse
import punishments.common.dto.response.PunishmentResponse
import punishments.common.dto.response.PunishmentSummaryResponse
import punishments.common.dto.response.ReasonCatalogResponse
import punishments.common.dto.response.RevokePunishmentResult
import punishments.common.event.PunishmentEvent
import punishments.common.grpc.PunishmentServiceGrpcKt
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentType
import punishments.common.protocol.PunishmentAPI
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class GrpcPunishmentClient(
    private val config: GrpcClientConfig,
    private val serviceToken: String = ""
) : PunishmentAPI, AutoCloseable {

    private val logger = LoggerFactory.getLogger(GrpcPunishmentClient::class.java)
    private val channel = BalancedGrpcChannel(config)

    init {
        channel.connect()
    }

    var eventConsumer: DualEventConsumer? = null
        private set

    fun createEventConsumer(
        cache: ClientSideCache = ClientSideCache(10),
        redisConfig: ClientConfig = ClientConfig(),
        eventHandler: EventHandler,
    ): DualEventConsumer {
        val consumer = DualEventConsumer(
            redisConsumer = RedisEventConsumer(redisConfig),
            deduplicator = EventDeduplicator(),
            eventHandler = eventHandler,
            cache = cache
        )
        eventConsumer = consumer
        return consumer
    }

    /**
     * Creates a new punishment based on the provided request.
     *
     * See [CreatePunishmentRequest] for details about the request parameters.
     */
    override suspend fun createPunishment(request: CreatePunishmentRequest): CreatePunishmentResult = retrying("createPunishment") {
        createStub().createPunishment(request.toProto()).toDomain()
    }

    /**
     * Revokes an existing punishment based on the provided request.
     *
     * See [RevokePunishmentRequest] for details about the request parameters.
     */
    override suspend fun revokePunishment(request: RevokePunishmentRequest): RevokePunishmentResult = retrying("revokePunishment") {
        createStub().revokePunishment(request.toProto()).toDomain()
    }

    /**
     * Gets detailed information about a specific punishment by its ID.
     * Returns null if no punishment with the given ID exists.
     *
     * See [GetPunishmentDetailsRequest] for details about the request parameters.
     */
    override suspend fun getPunishmentDetails(request: GetPunishmentDetailsRequest): PunishmentResponse? = retrying("getPunishmentDetails") {
        createStub().getPunishmentDetails(request.toProto()).toDomain()
    }

    /**
     * Gets a paginated list of punishments based on the provided request parameters, which filtered by [PunishmentType] and [PunishmentStatus].
     *
     * See [GetPunishmentsRequest] for details about the request parameters.
     */
    override suspend fun getPunishments(request: GetPunishmentsRequest): PaginatedResponse<PunishmentSummaryResponse> = retrying("getPunishments") {
        createStub().getPunishments(request.toProto()).toDomain()
    }

    /**
     * Gets a paginated list of punishments for a specific target based on the provided request parameters.
     *
     * See [GetTargetPunishmentsRequest] for details about the request parameters.
     */
    override suspend fun getTargetPunishments(request: GetTargetPunishmentsRequest): PaginatedResponse<PunishmentSummaryResponse> = retrying("getTargetPunishments") {
        createStub().getTargetPunishments(request.toProto()).toDomain()
    }

    /**
     *
     */
    override suspend fun searchPunishments(request: SearchPunishmentsRequest): PaginatedResponse<PunishmentSummaryResponse> = retrying("searchPunishments") {
        createStub().searchPunishments(request.toProto()).toDomain()
    }

    /**
     *
     */
    override suspend fun getCatalog(request: GetCatalogRequest): ReasonCatalogResponse = retrying("getCatalog") {
        createStub().getCatalog(request.toProto()).toDomain()
    }

    fun shutdown() {
        eventConsumer?.close()
        channel.shutdown()
    }

    override fun close() {
        shutdown()
    }

    private fun createStub(): PunishmentServiceGrpcKt.PunishmentServiceCoroutineStub {
        val stub = PunishmentServiceGrpcKt.PunishmentServiceCoroutineStub(channel.getChannel())
            .withDeadlineAfter(config.timeoutMs, TimeUnit.MILLISECONDS)

        return if (serviceToken.isNotBlank()) {
            stub.withInterceptors(AuthHeaderInterceptor(serviceToken))
        } else {
            stub
        }
    }

    private suspend fun <T> retrying(method: String, block: suspend () -> T): T {
        var lastException: Exception? = null

        for (attempt in 1..config.retryAttempts) {
            try {
                return block()
            } catch (e: StatusException) {
                lastException = e
                val retryable = e.status.code in RETRYABLE_STATUSES
                if (!retryable || attempt == config.retryAttempts) {
                    logger.warn(
                        "gRPC {} failed (attempt {}/{}): {} - {}",
                        method,
                        attempt,
                        config.retryAttempts,
                        e.status.code,
                        e.status.description
                    )
                    throw e
                }

                val delayMs = (100L * (1L shl (attempt - 1))).coerceAtMost(2_000L)
                logger.debug(
                    "gRPC {} retry {}/{} after {}ms: {}",
                    method,
                    attempt,
                    config.retryAttempts,
                    delayMs,
                    e.status.code
                )
                delay(delayMs.milliseconds)
            }
        }

        throw lastException ?: IllegalStateException("Retry loop exited unexpectedly")
    }

    private class AuthHeaderInterceptor(private val token: String) : ClientInterceptor {
        override fun <ReqT, RespT> interceptCall(
            method: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
            next: Channel
        ): ClientCall<ReqT, RespT> {
            return object : SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
                override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                    headers.put(AUTH_KEY, token)
                    super.start(responseListener, headers)
                }
            }
        }

        private companion object {
            val AUTH_KEY: Metadata.Key<String> =
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        }
    }

    private companion object {
        val RETRYABLE_STATUSES = setOf(
            Status.Code.UNAVAILABLE,
            Status.Code.DEADLINE_EXCEEDED,
            Status.Code.ABORTED,
            Status.Code.UNKNOWN,
            Status.Code.CANCELLED
        )
    }
}
