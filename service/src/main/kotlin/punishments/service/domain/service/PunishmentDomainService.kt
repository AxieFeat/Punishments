package punishments.service.domain.service

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import punishments.common.error.ErrorCode
import punishments.common.error.PunishmentAlreadyActiveException
import punishments.common.error.PunishmentAlreadyRevokedException
import punishments.common.error.PunishmentException
import punishments.common.error.PunishmentNotFoundException
import punishments.common.event.EventMetadata
import punishments.common.event.PunishmentEvent
import punishments.common.model.PunishmentCatalog
import punishments.common.model.PunishmentHistoryEntry
import punishments.common.model.PunishmentHistoryType
import punishments.common.model.PunishmentRecord
import punishments.common.model.PunishmentSort
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection
import punishments.common.protocol.PunishmentAPI
import punishments.common.config.PunishmentDefaults
import punishments.service.cache.CacheKeys
import punishments.service.cache.PunishmentCache
import punishments.service.config.AppConfig
import punishments.service.config.PunishmentServiceConfig
import punishments.service.domain.mapper.toResponse
import punishments.service.domain.mapper.toSummary
import punishments.service.domain.validation.InvalidPunishmentRequestException
import punishments.service.domain.validation.PunishmentValidator
import punishments.service.messaging.RedisEventPublisher
import punishments.service.persistence.repository.PunishmentRepository
import punishments.service.persistence.repository.RepositoryPage
import java.util.UUID
import kotlin.math.ceil
import kotlin.time.Instant

class PunishmentDomainService(
    private val repository: PunishmentRepository,
    private val catalog: PunishmentCatalog,
    private val validator: PunishmentValidator,
    private val cache: PunishmentCache,
    private val events: RedisEventPublisher,
    private val expirationService: ExpirationService,
    private val appConfig: AppConfig,
    private val serviceConfig: PunishmentServiceConfig,
    private val json: Json
) : PunishmentAPI {

    override suspend fun createPunishment(request: CreatePunishmentRequest): CreatePunishmentResult {
        return try {
            expirationService.processExpired()
            val reasonId = validator.normalizeReasonId(request.reasonId)
            val targets = validator.normalizeTargets(request.selection)
            validator.validateActor(request.issuer)
            validator.validateDuration(request.durationSeconds)

            val issuedAt = request.issuedAt ?: now()
            val scope = validator.effectiveScope(request.scope, request.type, reasonId)
            ensureNoActiveConflicts(request.type, targets, issuedAt.toEpochMilliseconds())

            val record = PunishmentRecord(
                id = UUID.randomUUID(),
                type = request.type,
                status = PunishmentStatus.ACTIVE,
                targets = targets,
                scope = scope,
                reasonId = reasonId,
                reasonText = request.reasonText?.takeIf(String::isNotBlank),
                issuedBy = request.issuer,
                issuedAt = issuedAt,
                expiresAt = request.durationSeconds?.let { duration -> issuedAt.plusSeconds(duration) }
            )

            repository.insert(
                record = record,
                selection = TargetSelection(selector = request.selection.selector, targets = targets),
                historyEntry = PunishmentHistoryEntry(
                    punishmentId = record.id,
                    type = PunishmentHistoryType.CREATED,
                    actor = request.issuer,
                    timestamp = issuedAt
                )
            )
            cache.invalidateAll()
            events.publish(record.createdEvent(request.selection.selector))
            CreatePunishmentResult.Success(listOf(record.id))
        } catch (e: PunishmentException) {
            CreatePunishmentResult.Error(e.errorCode, e.message)
        } catch (e: IllegalArgumentException) {
            CreatePunishmentResult.Error(ErrorCode.INVALID_REQUEST, e.message ?: "Invalid request")
        } catch (e: Exception) {
            CreatePunishmentResult.Error(ErrorCode.INTERNAL_ERROR, e.message ?: "Internal error")
        }
    }

    override suspend fun revokePunishment(request: RevokePunishmentRequest): RevokePunishmentResult {
        return try {
            expirationService.processExpired()
            validator.validateActor(request.actor)
            val record = repository.getById(request.punishmentId)
                ?: throw PunishmentNotFoundException(request.punishmentId.toString())

            when (record.status) {
                PunishmentStatus.REVOKED -> throw PunishmentAlreadyRevokedException(record.id.toString())
                PunishmentStatus.EXPIRED -> throw InvalidPunishmentRequestException("Expired punishment cannot be revoked")
                PunishmentStatus.ACTIVE -> revokeActive(request, record)
            }
        } catch (e: PunishmentException) {
            RevokePunishmentResult.Error(e.errorCode, e.message)
        } catch (e: IllegalArgumentException) {
            RevokePunishmentResult.Error(ErrorCode.INVALID_REQUEST, e.message ?: "Invalid request")
        } catch (e: Exception) {
            RevokePunishmentResult.Error(ErrorCode.INTERNAL_ERROR, e.message ?: "Internal error")
        }
    }

    override suspend fun getPunishment(request: GetPunishmentRequest): PunishmentResponse? {
        expirationService.processExpired()
        val cacheKey = CacheKeys.punishment(request.punishmentId.toString())
        cache.get(cacheKey)?.let { cached ->
            return json.decodeFromString<PunishmentResponse>(cached)
        }

        val response = repository.getById(request.punishmentId)?.toResponse() ?: return null
        cache.put(cacheKey, json.encodeToString(response))
        return response
    }

    override suspend fun getPunishments(
        request: GetPunishmentsRequest
    ): PaginatedResponse<PunishmentSummaryResponse> {
        expirationService.processExpired()
        val normalized = request.normalized()
        val cacheKey = CacheKeys.list(normalized)
        cache.get(cacheKey)?.let { cached ->
            return json.decodeFromString<PaginatedResponse<PunishmentSummaryResponse>>(cached)
        }

        val page = repository.list(
            targets = normalized.targets,
            type = normalized.type,
            status = normalized.status,
            sort = normalized.sort,
            page = normalized.page,
            pageSize = normalized.pageSize
        ).toResponse(normalized.page, normalized.pageSize)
        cache.put(cacheKey, json.encodeToString(page))
        return page
    }

    override suspend fun getTargetPunishments(
        request: GetTargetPunishmentsRequest
    ): PaginatedResponse<PunishmentSummaryResponse> {
        expirationService.processExpired()
        val page = request.page.coerceAtLeast(0)
        val pageSize = request.pageSize.normalizedPageSize()
        if (request.targets.isEmpty()) {
            return emptyPage(page, pageSize)
        }

        val cacheKey = CacheKeys.targetList(request.targets, page, pageSize)
        cache.get(cacheKey)?.let { cached ->
            return json.decodeFromString<PaginatedResponse<PunishmentSummaryResponse>>(cached)
        }

        val response = repository.list(
            targets = request.targets,
            type = null,
            status = null,
            sort = PunishmentSort.NEWEST,
            page = page,
            pageSize = pageSize
        ).toResponse(page, pageSize)
        cache.put(cacheKey, json.encodeToString(response))
        return response
    }

    override suspend fun searchPunishments(
        request: SearchPunishmentsRequest
    ): PaginatedResponse<PunishmentSummaryResponse> {
        expirationService.processExpired()
        val query = request.query.trim()
        val page = request.page.coerceAtLeast(0)
        val pageSize = request.pageSize.normalizedPageSize()
        if (query.isBlank()) {
            return emptyPage(page, pageSize)
        }

        val cacheKey = CacheKeys.search(query, page, pageSize)
        cache.get(cacheKey)?.let { cached ->
            return json.decodeFromString<PaginatedResponse<PunishmentSummaryResponse>>(cached)
        }

        val response = repository.search(query, page, pageSize).toResponse(page, pageSize)
        cache.put(cacheKey, json.encodeToString(response))
        return response
    }

    override suspend fun getCatalog(request: GetCatalogRequest): ReasonCatalogResponse {
        val version = request.version ?: serviceConfig.catalogVersion
        val cacheKey = CacheKeys.catalog(version)
        cache.get(cacheKey)?.let { cached ->
            return json.decodeFromString<ReasonCatalogResponse>(cached)
        }

        val response = ReasonCatalogResponse(catalog = catalog, version = version)
        cache.put(cacheKey, json.encodeToString(response))
        return response
    }

    private suspend fun revokeActive(
        request: RevokePunishmentRequest,
        record: PunishmentRecord
    ): RevokePunishmentResult {
        val revokedAt = now()
        val revoked = repository.revoke(
            id = request.punishmentId,
            actor = request.actor,
            revokedAtEpochMs = revokedAt.toEpochMilliseconds(),
            historyEntry = PunishmentHistoryEntry(
                punishmentId = request.punishmentId,
                type = PunishmentHistoryType.REVOKED,
                actor = request.actor,
                note = request.reason?.takeIf(String::isNotBlank),
                timestamp = revokedAt
            )
        )

        if (!revoked) {
            throw InvalidPunishmentRequestException("Punishment is no longer active")
        }

        cache.invalidate(CacheKeys.punishment(record.id.toString()))
        cache.invalidateAll()
        events.publish(
            PunishmentEvent.PunishmentRevoked(
                metadata = EventMetadata(sourceServer = appConfig.instanceId),
                punishmentId = record.id,
                actor = request.actor,
                reason = request.reason
            )
        )
        return RevokePunishmentResult.Success()
    }

    private suspend fun ensureNoActiveConflicts(
        type: PunishmentType,
        targets: List<PunishmentTarget>,
        nowEpochMs: Long
    ) {
        if (type == PunishmentType.WARN) {
            return
        }

        targets.forEach { target ->
            val conflictId = repository.findActiveConflict(type, target, nowEpochMs)
            if (conflictId != null) {
                throw PunishmentAlreadyActiveException(conflictId.toString())
            }
        }
    }

    private fun PunishmentRecord.createdEvent(selector: String?): PunishmentEvent.PunishmentCreated {
        return PunishmentEvent.PunishmentCreated(
            metadata = EventMetadata(sourceServer = appConfig.instanceId),
            punishmentId = id,
            type = type,
            selection = TargetSelection(selector = selector, targets = targets),
            reasonId = reasonId,
            actor = issuedBy
        )
    }

    private fun RepositoryPage<PunishmentRecord>.toResponse(
        page: Int,
        pageSize: Int
    ): PaginatedResponse<PunishmentSummaryResponse> {
        return PaginatedResponse(
            items = items.map(PunishmentRecord::toSummary),
            page = page,
            pageSize = pageSize,
            totalItems = totalItems,
            totalPages = if (totalItems == 0L) 0 else ceil(totalItems.toDouble() / pageSize).toInt()
        )
    }

    private fun GetPunishmentsRequest.normalized(): GetPunishmentsRequest {
        return copy(
            page = page.coerceAtLeast(0),
            pageSize = pageSize.normalizedPageSize()
        )
    }

    private fun Int.normalizedPageSize(): Int {
        return coerceIn(1, PunishmentDefaults.PAGE_SIZE)
    }

    private fun emptyPage(page: Int, pageSize: Int): PaginatedResponse<PunishmentSummaryResponse> {
        return PaginatedResponse(
            items = emptyList(),
            page = page,
            pageSize = pageSize,
            totalItems = 0,
            totalPages = 0
        )
    }

    private fun now(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())

    private fun Instant.plusSeconds(seconds: Long): Instant {
        return Instant.fromEpochMilliseconds(toEpochMilliseconds() + seconds * MILLIS_PER_SECOND)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
