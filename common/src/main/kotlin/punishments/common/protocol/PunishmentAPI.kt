package punishments.common.protocol

import punishments.common.dto.request.CreatePunishmentRequest
import punishments.common.dto.request.GetCatalogRequest
import punishments.common.dto.request.GetPunishmentRequest
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

/**
 * Core API contract for the punishment system.
 * Implemented by the service (server-side) and by the gRPC client (client-side).
 */
interface PunishmentAPI {

    suspend fun createPunishment(request: CreatePunishmentRequest): CreatePunishmentResult
    suspend fun revokePunishment(request: RevokePunishmentRequest): RevokePunishmentResult
    suspend fun getPunishment(request: GetPunishmentRequest): PunishmentResponse?
    suspend fun getPunishments(request: GetPunishmentsRequest): PaginatedResponse<PunishmentSummaryResponse>
    suspend fun getTargetPunishments(request: GetTargetPunishmentsRequest): PaginatedResponse<PunishmentSummaryResponse>
    suspend fun searchPunishments(request: SearchPunishmentsRequest): PaginatedResponse<PunishmentSummaryResponse>
    suspend fun getCatalog(request: GetCatalogRequest = GetCatalogRequest()): ReasonCatalogResponse
}
