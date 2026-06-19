package punishments.service.domain.service

import kotlinx.serialization.json.Json
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
import punishments.common.dto.response.PaginatedResponse
import punishments.common.dto.response.ActiveRestrictionResponse
import punishments.common.dto.response.PunishmentResponse
import punishments.common.dto.response.PunishmentSummaryResponse
import punishments.common.dto.response.ReasonCatalogResponse
import punishments.common.dto.response.RevokePunishmentResult
import punishments.common.dto.response.TargetRestrictionsResponse
import punishments.common.error.ErrorCode
import punishments.common.error.PunishmentAlreadyRevokedException
import punishments.common.error.PunishmentException
import punishments.common.error.PunishmentNotFoundException
import punishments.common.event.EventMetadata
import punishments.common.event.PunishmentEvent
import punishments.common.model.PunishmentCatalog
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentHistoryEntry
import punishments.common.model.PunishmentHistoryType
import punishments.common.model.PunishmentRecord
import punishments.common.model.PunishmentSort
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentSummaryRecord
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection
import punishments.common.protocol.PunishmentAPI
import punishments.service.cache.CacheKeys
import punishments.service.cache.TieredPunishmentCache
import punishments.service.config.AppConfig
import punishments.service.config.PunishmentServiceConfig
import punishments.service.domain.mapper.toResponse
import punishments.service.domain.mapper.toSummary
import punishments.service.domain.validation.InvalidPunishmentRequestException
import punishments.service.domain.validation.PunishmentValidator
import punishments.service.messaging.RedisEventPublisher
import punishments.service.metrics.CacheFamily
import punishments.service.metrics.CacheTier
import punishments.service.metrics.PunishmentMetrics
import punishments.service.persistence.repository.ActiveRestrictionRecord
import punishments.service.persistence.repository.impl.ActiveRestrictionConflictException
import punishments.service.persistence.repository.PunishmentRepository
import punishments.service.persistence.repository.RepositoryPage
import punishments.common.util.ValidationUtils
import java.util.UUID
import kotlin.math.ceil
import kotlin.time.Instant

/**
 * Application service that separates command, query and enforcement semantics on top
 * of one public API contract.
 *
 * Commands use DB transactions and idempotency for retry safety. Mutable reads use
 * revisioned cache entries with DB fallback, so list/search/enforcement paths do
 * not serve stale active punishments after a write.
 */
class PunishmentDomainService(
    private val repository: PunishmentRepository,
    private val catalog: PunishmentCatalog,
    private val validator: PunishmentValidator,
    private val cache: TieredPunishmentCache,
    private val events: RedisEventPublisher,
    private val appConfig: AppConfig,
    private val serviceConfig: PunishmentServiceConfig,
    private val json: Json,
    private val metrics: PunishmentMetrics? = null
) : PunishmentAPI {

    override suspend fun createPunishment(request: CreatePunishmentRequest): CreatePunishmentResult {
        return try {
            idempotentCommand(
                operation = "create",
                requestId = request.requestId,
                request = request,
                decode = { payload -> json.decodeFromString<CreatePunishmentResult>(payload) },
                encode = { result -> json.encodeToString(result) }
            ) {
                try {
                    val reasonId = validator.normalizeReasonId(request.reasonId)
                    val reasonText = ValidationUtils.normalizeReasonText(request.reasonText)
                    val targets = validator.normalizeTargets(request.selection)
                    val issuer = validator.normalizeActor(request.issuer)
                    validator.validateDuration(request.durationSeconds)

                    val issuedAt = request.issuedAt ?: now()
                    val scope = validator.effectiveScope(request.scope, request.type, reasonId)

                    val record = PunishmentRecord(
                        id = UUID.randomUUID(),
                        type = request.type,
                        status = request.type.initialStatus(),
                        targets = targets,
                        targetSelector = request.selection.selector,
                        scope = scope,
                        reasonId = reasonId,
                        reasonText = reasonText,
                        issuedBy = issuer,
                        issuedAt = issuedAt,
                        expiresAt = request.type.expiresAt(issuedAt, request.durationSeconds)
                    )

                    // Active BAN/MUTE conflicts are protected by the repository's unique
                    // active restriction insert, not by a pre-read that could race.
                    repository.insert(
                        record = record,
                        selection = TargetSelection(selector = request.selection.selector, targets = targets),
                        historyEntry = PunishmentHistoryEntry(
                            punishmentId = record.id,
                            type = PunishmentHistoryType.CREATED,
                            actor = issuer,
                            timestamp = issuedAt
                        )
                    )
                    invalidateAfterMutation(record)
                    events.publish(record.createdEvent())
                    metrics?.punishmentsCreated?.increment(record.targets.size.toDouble())
                    CreatePunishmentResult.Success(listOf(record.id))
                } catch (e: ActiveRestrictionConflictException) {
                    CreatePunishmentResult.Error(ErrorCode.PUNISHMENT_ALREADY_ACTIVE, e.message ?: "Active punishment already exists")
                } catch (e: PunishmentException) {
                    CreatePunishmentResult.Error(e.errorCode, e.message)
                } catch (e: IllegalArgumentException) {
                    CreatePunishmentResult.Error(ErrorCode.INVALID_REQUEST, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    CreatePunishmentResult.Error(ErrorCode.INTERNAL_ERROR, e.message ?: "Internal error")
                }
            }
        } catch (e: IllegalArgumentException) {
            CreatePunishmentResult.Error(ErrorCode.INVALID_REQUEST, e.message ?: "Invalid request")
        }
    }

    override suspend fun revokePunishment(request: RevokePunishmentRequest): RevokePunishmentResult {
        return try {
            idempotentCommand(
                operation = "revoke",
                requestId = request.requestId,
                request = request,
                decode = { payload -> json.decodeFromString<RevokePunishmentResult>(payload) },
                encode = { result -> json.encodeToString(result) }
            ) {
                try {
                    val actor = validator.normalizeActor(request.actor)
                    val reason = ValidationUtils.normalizeReasonText(request.reason)
                    val record = repository.getById(request.punishmentId)
                        ?: throw PunishmentNotFoundException(request.punishmentId.toString())

                    when (record.status) {
                        PunishmentStatus.REVOKED -> throw PunishmentAlreadyRevokedException(record.id.toString())
                        PunishmentStatus.EXPIRED -> throw InvalidPunishmentRequestException("Expired punishment cannot be revoked")
                        PunishmentStatus.ACTIVE -> revokeActive(request, record, actor, reason)
                    }
                } catch (e: PunishmentException) {
                    RevokePunishmentResult.Error(e.errorCode, e.message)
                } catch (e: IllegalArgumentException) {
                    RevokePunishmentResult.Error(ErrorCode.INVALID_REQUEST, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    RevokePunishmentResult.Error(ErrorCode.INTERNAL_ERROR, e.message ?: "Internal error")
                }
            }
        } catch (e: IllegalArgumentException) {
            RevokePunishmentResult.Error(ErrorCode.INVALID_REQUEST, e.message ?: "Invalid request")
        }
    }

    override suspend fun getPunishmentDetails(request: GetPunishmentDetailsRequest): PunishmentResponse? {
        val cacheKey = CacheKeys.punishment(request.punishmentId.toString())
        val revisionKey = CacheKeys.punishmentRevision(request.punishmentId.toString())
        cache.getStrictDetails(cacheKey, revisionKey)?.let { cached ->
            return json.decodeFromString<PunishmentResponse>(cached)
        }

        val response = repository.getById(request.punishmentId)?.toResponse()
        if (response == null) {
            metrics?.cacheTierMiss(CacheFamily.STRICT_DETAILS, CacheTier.L3)
            return null
        }
        cache.putStrictDetails(cacheKey, revisionKey, json.encodeToString(response))
        metrics?.cacheTierHit(CacheFamily.STRICT_DETAILS, CacheTier.L3)
        return response
    }

    override suspend fun getPunishments(
        request: GetPunishmentsRequest
    ): PaginatedResponse<PunishmentSummaryResponse> {
        val normalized = request.normalized()
        val cacheKey = CacheKeys.list(normalized)
        cache.getListing(cacheKey)?.let { cached ->
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
        cache.putListing(cacheKey, json.encodeToString(page))
        metrics?.cacheTierHit(CacheFamily.LISTINGS, CacheTier.L3)
        return page
    }

    override suspend fun getTargetPunishments(
        request: GetTargetPunishmentsRequest
    ): PaginatedResponse<PunishmentSummaryResponse> {
        val page = ValidationUtils.normalizePage(request.page)
        val pageSize = ValidationUtils.normalizePageSize(request.pageSize)
        val targets = ValidationUtils.normalizeTargets(request.targets)
        if (targets.isEmpty()) {
            return emptyPage(page, pageSize)
        }

        val cacheKey = CacheKeys.targetList(targets, page, pageSize)
        cache.getListing(cacheKey)?.let { cached ->
            return json.decodeFromString<PaginatedResponse<PunishmentSummaryResponse>>(cached)
        }

        val response = repository.list(
            targets = targets,
            type = null,
            status = null,
            sort = PunishmentSort.NEWEST,
            page = page,
            pageSize = pageSize
        ).toResponse(page, pageSize)
        cache.putListing(cacheKey, json.encodeToString(response))
        metrics?.cacheTierHit(CacheFamily.LISTINGS, CacheTier.L3)
        return response
    }

    override suspend fun searchPunishments(
        request: SearchPunishmentsRequest
    ): PaginatedResponse<PunishmentSummaryResponse> {
        val query = ValidationUtils.normalizeSearchQuery(request.query)
        val page = ValidationUtils.normalizePage(request.page)
        val pageSize = ValidationUtils.normalizePageSize(request.pageSize)
        if (query.isBlank()) {
            return emptyPage(page, pageSize)
        }

        val cacheKey = CacheKeys.search(query, page, pageSize)
        cache.getSearch(cacheKey)?.let { cached ->
            return json.decodeFromString<PaginatedResponse<PunishmentSummaryResponse>>(cached)
        }

        val response = repository.search(query, page, pageSize).toResponse(page, pageSize)
        cache.putSearch(cacheKey, json.encodeToString(response))
        metrics?.cacheTierHit(CacheFamily.SEARCH, CacheTier.L3)
        return response
    }

    override suspend fun getCatalog(request: GetCatalogRequest): ReasonCatalogResponse {
        val version = request.version ?: serviceConfig.catalogVersion
        val cacheKey = CacheKeys.catalog(version)
        cache.getCatalog(cacheKey)?.let { cached ->
            return json.decodeFromString<ReasonCatalogResponse>(cached)
        }

        val response = ReasonCatalogResponse(catalog = catalog, version = version)
        cache.putCatalog(cacheKey, json.encodeToString(response))
        metrics?.cacheTierHit(CacheFamily.CATALOG, CacheTier.L3)
        return response
    }

    override suspend fun checkTargetRestrictions(request: CheckTargetRestrictionsRequest): TargetRestrictionsResponse {
        metrics?.enforcementChecks?.increment()
        val targets = ValidationUtils.normalizeTargets(request.targets)
        val restrictionKeys = ValidationUtils.normalizeRestrictionKeys(request.restrictionKeys)
        if (targets.isEmpty()) {
            return TargetRestrictionsResponse(restricted = false, restrictions = emptyList())
        }

        val cacheKey = CacheKeys.activeRestrictions(targets, request.types, restrictionKeys)
        val revisionKeys = targetRevisionKeys(targets)
        cache.getStrictTargetActive(cacheKey, revisionKeys)?.let { cached ->
            val response = json.decodeFromString<TargetRestrictionsResponse>(cached)
            if (response.restricted) metrics?.enforcementRestricted?.increment()
            return response
        }

        val response = repository.findActiveRestrictions(
            targets = targets,
            types = request.types,
            restrictionKeys = restrictionKeys,
            nowEpochMs = System.currentTimeMillis()
        ).toRestrictionsResponse()
        // Enforcement never treats cache/Redis failure as "not restricted"; the DB is
        // authoritative. Only positive restrictions are cached, so a missed
        // invalidation cannot preserve an unsafe "allowed" response.
        if (response.restricted) {
            cache.putStrictTargetActive(cacheKey, revisionKeys, json.encodeToString(response))
        }
        metrics?.cacheTierHit(CacheFamily.STRICT_TARGET_ACTIVE, CacheTier.L3)
        if (response.restricted) metrics?.enforcementRestricted?.increment()
        return response
    }

    override suspend fun getActiveRestrictions(request: GetActiveRestrictionsRequest): TargetRestrictionsResponse {
        val targets = ValidationUtils.normalizeTargets(request.targets)
        if (targets.isEmpty()) {
            return TargetRestrictionsResponse(restricted = false, restrictions = emptyList())
        }

        val cacheKey = CacheKeys.activeRestrictions(targets, request.types, emptySet())
        val revisionKeys = targetRevisionKeys(targets)
        cache.getStrictTargetActive(cacheKey, revisionKeys)?.let { cached ->
            return json.decodeFromString<TargetRestrictionsResponse>(cached)
        }

        val response = repository.findActiveRestrictions(
            targets = targets,
            types = request.types,
            restrictionKeys = emptySet(),
            nowEpochMs = System.currentTimeMillis()
        ).toRestrictionsResponse()
        if (response.restricted) {
            cache.putStrictTargetActive(cacheKey, revisionKeys, json.encodeToString(response))
        }
        metrics?.cacheTierHit(CacheFamily.STRICT_TARGET_ACTIVE, CacheTier.L3)
        return response
    }

    private suspend fun revokeActive(
        request: RevokePunishmentRequest,
        record: PunishmentRecord,
        actor: PunishmentActor,
        reason: String?
    ): RevokePunishmentResult {
        val revokedAt = now()
        val revoked = repository.revoke(
            id = request.punishmentId,
            actor = actor,
            revokedAtEpochMs = revokedAt.toEpochMilliseconds(),
            historyEntry = PunishmentHistoryEntry(
                punishmentId = request.punishmentId,
                type = PunishmentHistoryType.REVOKED,
                actor = actor,
                note = reason,
                timestamp = revokedAt
            )
        )

        if (!revoked) {
            throw InvalidPunishmentRequestException("Punishment is no longer active")
        }

        val revokedRecord = record.copy(
            status = PunishmentStatus.REVOKED,
            revokedAt = revokedAt,
            revokedBy = actor
        )
        invalidateAfterMutation(record)
        events.publish(
            PunishmentEvent.PunishmentRevoked(
                metadata = EventMetadata(sourceServer = appConfig.instanceId),
                punishment = revokedRecord
            )
        )
        metrics?.punishmentsRevoked?.increment()
        return RevokePunishmentResult.Success()
    }

    private fun PunishmentRecord.createdEvent(): PunishmentEvent.PunishmentCreated {
        return PunishmentEvent.PunishmentCreated(
            metadata = EventMetadata(sourceServer = appConfig.instanceId),
            punishment = this
        )
    }

    private suspend fun invalidateAfterMutation(record: PunishmentRecord) {
        cache.invalidatePunishment(record.id.toString())
        cache.invalidateTargets(targetRevisionKeys(record.targets))
        cache.invalidateMutableReads()
        refreshActiveRestrictionGauge()
    }

    private suspend fun refreshActiveRestrictionGauge() {
        metrics?.setActiveRestrictions(repository.countActiveRestrictions(System.currentTimeMillis()))
    }

    private fun targetRevisionKeys(targets: List<PunishmentTarget>): List<String> {
        return targets
            .flatMap { target ->
                buildList {
                    target.id?.let { id -> add(CacheKeys.targetIdRevision(id)) }
                    target.name?.takeIf(String::isNotBlank)?.let { name ->
                        add(CacheKeys.targetNameRevision(target.targetType, name))
                    }
                }
            }
            .distinct()
    }

    private fun List<ActiveRestrictionRecord>.toRestrictionsResponse(): TargetRestrictionsResponse {
        val restrictions = map { record ->
            ActiveRestrictionResponse(
                punishmentId = record.punishmentId,
                type = record.type,
                target = record.target,
                restrictionKeys = record.restrictionKeys,
                reasonId = record.reasonId,
                expiresAt = record.expiresAtEpochMs?.let(Instant::fromEpochMilliseconds)
            )
        }
        return TargetRestrictionsResponse(restricted = restrictions.isNotEmpty(), restrictions = restrictions)
    }

    private suspend inline fun <reified Request, Result> idempotentCommand(
        operation: String,
        requestId: String?,
        request: Request,
        decode: (String) -> Result,
        encode: (Result) -> String,
        block: suspend () -> Result
    ): Result {
        val normalizedRequestId = ValidationUtils.normalizeRequestId(requestId) ?: return block()

        val requestHash = CacheKeys.run { json.encodeToString(request).sha256() }
        repository.findIdempotencyResult(operation, normalizedRequestId, requestHash)?.let { payload ->
            return decode(payload)
        }

        val result = block()
        repository.storeIdempotencyResult(
            operation = operation,
            requestId = normalizedRequestId,
            requestHash = requestHash,
            resultJson = encode(result),
            createdAtEpochMs = System.currentTimeMillis()
        )
        return result
    }

    private fun RepositoryPage<PunishmentSummaryRecord>.toResponse(
        page: Int,
        pageSize: Int
    ): PaginatedResponse<PunishmentSummaryResponse> {
        return PaginatedResponse(
            items = items.map(PunishmentSummaryRecord::toSummary),
            page = page,
            pageSize = pageSize,
            totalItems = totalItems,
            totalPages = if (totalItems == 0L) 0 else ceil(totalItems.toDouble() / pageSize).toInt()
        )
    }

    private fun GetPunishmentsRequest.normalized(): GetPunishmentsRequest {
        return copy(
            targets = ValidationUtils.normalizeTargets(targets),
            page = ValidationUtils.normalizePage(page),
            pageSize = ValidationUtils.normalizePageSize(pageSize)
        )
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

    private fun PunishmentType.initialStatus(): PunishmentStatus {
        return if (this == PunishmentType.KICK) PunishmentStatus.EXPIRED else PunishmentStatus.ACTIVE
    }

    private fun PunishmentType.expiresAt(issuedAt: Instant, durationSeconds: Long?): Instant? {
        if (this == PunishmentType.KICK) {
            return null
        }
        return durationSeconds?.let { seconds -> issuedAt.plusSeconds(seconds) }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
