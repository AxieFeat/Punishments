package punishments.client.common.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import punishments.client.common.config.ClientConfig
import punishments.common.dto.request.CheckTargetRestrictionsRequest
import punishments.common.dto.request.CreatePunishmentRequest
import punishments.common.dto.request.GetActiveRestrictionsRequest
import punishments.common.dto.request.GetCatalogRequest
import punishments.common.dto.request.GetPunishmentDetailsRequest
import punishments.common.dto.request.GetPunishmentsRequest
import punishments.common.dto.request.GetTargetPunishmentsRequest
import punishments.common.dto.request.RevokePunishmentRequest
import punishments.common.dto.request.SearchPunishmentsRequest
import punishments.common.dto.response.CreatePunishmentResult
import punishments.common.dto.response.ErrorResponse
import punishments.common.dto.response.PaginatedResponse
import punishments.common.dto.response.PunishmentResponse
import punishments.common.dto.response.PunishmentSummaryResponse
import punishments.common.dto.response.ReasonCatalogResponse
import punishments.common.dto.response.RevokePunishmentResult
import punishments.common.dto.response.TargetRestrictionsResponse
import punishments.common.protocol.PunishmentAPI
import punishments.common.protocol.Routes
import punishments.common.serialization.CommonSerializersModule

class HttpPunishmentClient(
    private val config: ClientConfig,
    private val serviceToken: String = config.serviceToken,
    private val client: HttpClient = defaultHttpClient(config)
) : PunishmentAPI, AutoCloseable {

    override suspend fun createPunishment(request: CreatePunishmentRequest): CreatePunishmentResult {
        return postJson(Routes.PUNISHMENTS, request)
    }

    override suspend fun revokePunishment(request: RevokePunishmentRequest): RevokePunishmentResult {
        return postJson(Routes.punishmentRevoke(request.punishmentId), request)
    }

    override suspend fun getPunishmentDetails(request: GetPunishmentDetailsRequest): PunishmentResponse? {
        val response = client.get(endpoint(Routes.punishmentById(request.punishmentId))) {
            authorized()
            accept(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.NotFound) {
            return null
        }
        return response.decode()
    }

    override suspend fun getPunishments(
        request: GetPunishmentsRequest
    ): PaginatedResponse<PunishmentSummaryResponse> {
        return postJson(Routes.PUNISHMENT_LIST, request)
    }

    override suspend fun getTargetPunishments(
        request: GetTargetPunishmentsRequest
    ): PaginatedResponse<PunishmentSummaryResponse> {
        return postJson(Routes.TARGET_PUNISHMENTS, request)
    }

    override suspend fun searchPunishments(
        request: SearchPunishmentsRequest
    ): PaginatedResponse<PunishmentSummaryResponse> {
        return postJson(Routes.PUNISHMENT_SEARCH, request)
    }

    override suspend fun getCatalog(request: GetCatalogRequest): ReasonCatalogResponse {
        return postJson(Routes.CATALOG, request)
    }

    override suspend fun checkTargetRestrictions(
        request: CheckTargetRestrictionsRequest
    ): TargetRestrictionsResponse {
        return postJson(Routes.TARGET_RESTRICTIONS_CHECK, request)
    }

    override suspend fun getActiveRestrictions(
        request: GetActiveRestrictionsRequest
    ): TargetRestrictionsResponse {
        return postJson(Routes.ACTIVE_RESTRICTIONS, request)
    }

    override fun close() {
        client.close()
    }

    private suspend inline fun <reified Request : Any, reified Response> postJson(
        route: String,
        request: Request
    ): Response {
        val response = client.post(endpoint(route)) {
            authorized()
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
        }
        return response.decode()
    }

    private fun endpoint(route: String): String {
        return "${config.httpBaseUrl.trimEnd('/')}$route"
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorized() {
        if (serviceToken.isNotBlank()) {
            header(HttpHeaders.Authorization, serviceToken)
        }
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        if (status.value in SUCCESS_STATUS_RANGE) {
            return body()
        }

        val error = runCatching { body<ErrorResponse>() }.getOrNull()
        val message = error?.message ?: bodyAsText().ifBlank { "HTTP ${status.value}" }
        throw HttpPunishmentException(status, error, message)
    }

    companion object {
        fun defaultHttpClient(config: ClientConfig): HttpClient {
            return HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            encodeDefaults = true
                            serializersModule = CommonSerializersModule
                        }
                    )
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = config.httpTimeoutMs
                }
            }
        }
    }
}

class HttpPunishmentException(
    val status: HttpStatusCode,
    val error: ErrorResponse?,
    message: String
) : RuntimeException(message)

private val SUCCESS_STATUS_RANGE = 200..299
