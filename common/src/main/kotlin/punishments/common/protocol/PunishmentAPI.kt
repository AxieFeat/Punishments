package punishments.common.protocol

import punishments.common.dto.request.CreatePunishmentRequest
import punishments.common.dto.request.CheckTargetRestrictionsRequest
import punishments.common.dto.request.GetActiveRestrictionsRequest
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
import punishments.common.dto.response.TargetRestrictionsResponse

/**
 * Core API contract for the punishment system.
 * Implemented by the service (server-side) and by the gRPC client (client-side).
 */
interface PunishmentAPI {

    suspend fun createPunishment(request: CreatePunishmentRequest): CreatePunishmentResult
    suspend fun revokePunishment(request: RevokePunishmentRequest): RevokePunishmentResult
    suspend fun getPunishmentDetails(request: GetPunishmentDetailsRequest): PunishmentResponse?
    suspend fun getPunishments(request: GetPunishmentsRequest): PaginatedResponse<PunishmentSummaryResponse>
    suspend fun getTargetPunishments(request: GetTargetPunishmentsRequest): PaginatedResponse<PunishmentSummaryResponse>
    suspend fun searchPunishments(request: SearchPunishmentsRequest): PaginatedResponse<PunishmentSummaryResponse>
    suspend fun getCatalog(request: GetCatalogRequest = GetCatalogRequest()): ReasonCatalogResponse
    suspend fun checkTargetRestrictions(request: CheckTargetRestrictionsRequest): TargetRestrictionsResponse
    suspend fun getActiveRestrictions(request: GetActiveRestrictionsRequest): TargetRestrictionsResponse
}
