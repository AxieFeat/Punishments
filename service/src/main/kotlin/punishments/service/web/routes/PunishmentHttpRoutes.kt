package punishments.service.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import punishments.common.dto.request.CheckTargetRestrictionsRequest
import punishments.common.dto.request.CreatePunishmentRequest
import punishments.common.dto.request.GetActiveRestrictionsRequest
import punishments.common.dto.request.GetCatalogRequest
import punishments.common.dto.request.GetPunishmentsRequest
import punishments.common.dto.request.GetPunishmentDetailsRequest
import punishments.common.dto.request.GetTargetPunishmentsRequest
import punishments.common.dto.request.RevokePunishmentRequest
import punishments.common.dto.request.SearchPunishmentsRequest
import punishments.common.dto.response.ErrorResponse
import punishments.common.error.ErrorCode
import punishments.common.error.PunishmentException
import punishments.common.protocol.PunishmentAPI
import punishments.common.protocol.Routes
import punishments.service.web.plugins.appMeterRegistry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.UUID

fun Route.punishmentHttpRoutes(
    api: PunishmentAPI,
    expectedToken: String,
    meterRegistry: MeterRegistry = appMeterRegistry
) {
    post(Routes.PUNISHMENTS) {
        call.recordHttpApiCall("CreatePunishment", Routes.PUNISHMENTS, METHOD_POST, meterRegistry) {
            authorized(expectedToken) {
                respondApi {
                    respond(api.createPunishment(receive<CreatePunishmentRequest>()))
                }
            }
        }
    }

    post(Routes.PUNISHMENT_LIST) {
        call.recordHttpApiCall("GetPunishments", Routes.PUNISHMENT_LIST, METHOD_POST, meterRegistry) {
            authorized(expectedToken) {
                respondApi {
                    respond(api.getPunishments(receive<GetPunishmentsRequest>()))
                }
            }
        }
    }

    post(Routes.PUNISHMENT_SEARCH) {
        call.recordHttpApiCall("SearchPunishments", Routes.PUNISHMENT_SEARCH, METHOD_POST, meterRegistry) {
            authorized(expectedToken) {
                respondApi {
                    respond(api.searchPunishments(receive<SearchPunishmentsRequest>()))
                }
            }
        }
    }

    get(Routes.PUNISHMENT_BY_ID) {
        call.recordHttpApiCall("GetPunishmentDetails", Routes.PUNISHMENT_BY_ID, METHOD_GET, meterRegistry) {
            authorized(expectedToken) {
                respondApi {
                    val punishmentId = UUID.fromString(requiredPathParameter("id"))
                    val response = api.getPunishmentDetails(GetPunishmentDetailsRequest(punishmentId))
                    if (response == null) {
                        respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse(ErrorCode.PUNISHMENT_NOT_FOUND, "Punishment not found: $punishmentId")
                        )
                    } else {
                        respond(response)
                    }
                }
            }
        }
    }

    post(Routes.PUNISHMENT_REVOKE) {
        call.recordHttpApiCall("RevokePunishment", Routes.PUNISHMENT_REVOKE, METHOD_POST, meterRegistry) {
            authorized(expectedToken) {
                respondApi {
                    val punishmentId = UUID.fromString(requiredPathParameter("id"))
                    val request = receive<RevokePunishmentRequest>().copy(punishmentId = punishmentId)
                    respond(api.revokePunishment(request))
                }
            }
        }
    }

    post(Routes.TARGET_PUNISHMENTS) {
        call.recordHttpApiCall("GetTargetPunishments", Routes.TARGET_PUNISHMENTS, METHOD_POST, meterRegistry) {
            authorized(expectedToken) {
                respondApi {
                    respond(api.getTargetPunishments(receive<GetTargetPunishmentsRequest>()))
                }
            }
        }
    }

    get(Routes.CATALOG) {
        call.recordHttpApiCall("GetCatalog", Routes.CATALOG, METHOD_GET, meterRegistry) {
            authorized(expectedToken) {
                respondApi {
                    respond(api.getCatalog(GetCatalogRequest()))
                }
            }
        }
    }

    post(Routes.CATALOG) {
        call.recordHttpApiCall("GetCatalog", Routes.CATALOG, METHOD_POST, meterRegistry) {
            authorized(expectedToken) {
                respondApi {
                    respond(api.getCatalog(receive<GetCatalogRequest>()))
                }
            }
        }
    }

    post(Routes.TARGET_RESTRICTIONS_CHECK) {
        call.recordHttpApiCall("CheckTargetRestrictions", Routes.TARGET_RESTRICTIONS_CHECK, METHOD_POST, meterRegistry) {
            authorized(expectedToken) {
                respondApi {
                    respond(api.checkTargetRestrictions(receive<CheckTargetRestrictionsRequest>()))
                }
            }
        }
    }

    post(Routes.ACTIVE_RESTRICTIONS) {
        call.recordHttpApiCall("GetActiveRestrictions", Routes.ACTIVE_RESTRICTIONS, METHOD_POST, meterRegistry) {
            authorized(expectedToken) {
                respondApi {
                    respond(api.getActiveRestrictions(receive<GetActiveRestrictionsRequest>()))
                }
            }
        }
    }
}

private suspend fun ApplicationCall.authorized(
    expectedToken: String,
    block: suspend ApplicationCall.() -> Unit
) {
    if (expectedToken.isBlank() || request.headers[AUTHORIZATION_HEADER].matchesToken(expectedToken)) {
        block()
        return
    }

    respond(
        HttpStatusCode.Unauthorized,
        ErrorResponse(ErrorCode.AUTHENTICATION_FAILED, "Invalid or missing auth token")
    )
}

private suspend fun ApplicationCall.respondApi(block: suspend ApplicationCall.() -> Unit) {
    try {
        block()
    } catch (e: PunishmentException) {
        respond(e.httpStatus(), ErrorResponse(e.errorCode, e.message))
    } catch (e: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorCode.INVALID_REQUEST, e.message ?: "Invalid request"))
    } catch (_: Exception) {
        respond(HttpStatusCode.InternalServerError, ErrorResponse(ErrorCode.INTERNAL_ERROR, "Internal error"))
    }
}

private fun ApplicationCall.requiredPathParameter(name: String): String {
    return parameters[name] ?: throw IllegalArgumentException("Missing path parameter: $name")
}

private fun PunishmentException.httpStatus(): HttpStatusCode {
    return when (errorCode) {
        ErrorCode.PUNISHMENT_NOT_FOUND,
        ErrorCode.TARGET_NOT_FOUND,
        ErrorCode.REASON_NOT_FOUND -> HttpStatusCode.NotFound
        ErrorCode.PUNISHMENT_ALREADY_REVOKED,
        ErrorCode.PUNISHMENT_ALREADY_ACTIVE -> HttpStatusCode.Conflict
        ErrorCode.INVALID_SCOPE,
        ErrorCode.INVALID_REQUEST -> HttpStatusCode.BadRequest
        ErrorCode.AUTHENTICATION_FAILED -> HttpStatusCode.Unauthorized
        ErrorCode.INTERNAL_ERROR -> HttpStatusCode.InternalServerError
    }
}

private fun String?.matchesToken(expectedToken: String): Boolean {
    return this == expectedToken || this == "Bearer $expectedToken"
}

private suspend fun ApplicationCall.recordHttpApiCall(
    operation: String,
    route: String,
    method: String,
    meterRegistry: MeterRegistry,
    block: suspend ApplicationCall.() -> Unit
) {
    val startNanos = System.nanoTime()
    var status = STATUS_UNHANDLED
    try {
        block()
        status = response.status()?.value?.toString() ?: STATUS_OK
    } catch (e: Throwable) {
        status = response.status()?.value?.toString() ?: STATUS_INTERNAL_ERROR
        throw e
    } finally {
        meterRegistry.counter(
            "punishments.http.server.requests",
            TAG_OPERATION, operation,
            TAG_ROUTE, route,
            TAG_METHOD, method,
            TAG_STATUS, status
        ).increment()

        httpTimer(operation, route, method, status, meterRegistry)
            .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS)
    }
}

private fun httpTimer(
    operation: String,
    route: String,
    method: String,
    status: String,
    meterRegistry: MeterRegistry
): Timer {
    val key = "$operation|$route|$method|$status"
    return httpTimerCache.computeIfAbsent(key) {
        Timer.builder("punishments.http.server.request.duration")
            .description("Punishment HTTP API request duration")
            .tag(TAG_OPERATION, operation)
            .tag(TAG_ROUTE, route)
            .tag(TAG_METHOD, method)
            .tag(TAG_STATUS, status)
            .publishPercentileHistogram()
            .publishPercentiles(0.5, 0.95, 0.99)
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .register(meterRegistry)
    }
}

private const val AUTHORIZATION_HEADER = "authorization"
private const val METHOD_GET = "GET"
private const val METHOD_POST = "POST"
private const val STATUS_OK = "200"
private const val STATUS_INTERNAL_ERROR = "500"
private const val STATUS_UNHANDLED = "unhandled"
private const val TAG_OPERATION = "operation"
private const val TAG_ROUTE = "route"
private const val TAG_METHOD = "method"
private const val TAG_STATUS = "status"

private val httpTimerCache = ConcurrentHashMap<String, Timer>()
