package punishments.client.common.network.mapper

import punishments.common.dto.ActorDto
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
import punishments.common.error.ErrorCode
import punishments.common.grpc.CreatePunishmentProto
import punishments.common.grpc.CreatePunishmentResultProto
import punishments.common.grpc.GetCatalogProto
import punishments.common.grpc.GetPunishmentDetailsProto
import punishments.common.grpc.GetPunishmentsProto
import punishments.common.grpc.GetTargetPunishmentsProto
import punishments.common.grpc.PaginatedPunishmentsProto
import punishments.common.grpc.PunishmentActorProto
import punishments.common.grpc.PunishmentCapabilityProto
import punishments.common.grpc.PunishmentReasonProto
import punishments.common.grpc.PunishmentResponseProto
import punishments.common.grpc.PunishmentScopeProto
import punishments.common.grpc.PunishmentSummaryProto
import punishments.common.grpc.PunishmentTargetProto
import punishments.common.grpc.ReasonCatalogProto
import punishments.common.grpc.RevokePunishmentProto
import punishments.common.grpc.RevokePunishmentResultProto
import punishments.common.grpc.SearchPunishmentsProto
import punishments.common.grpc.TargetSelectionProto
import punishments.common.grpc.createPunishmentProto
import punishments.common.grpc.getCatalogProto
import punishments.common.grpc.getPunishmentDetailsProto
import punishments.common.grpc.getPunishmentsProto
import punishments.common.grpc.getTargetPunishmentsProto
import punishments.common.grpc.punishmentActorProto
import punishments.common.grpc.punishmentScopeProto
import punishments.common.grpc.punishmentTargetProto
import punishments.common.grpc.revokePunishmentProto
import punishments.common.grpc.searchPunishmentsProto
import punishments.common.model.ActorSource
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentCapability
import punishments.common.model.PunishmentCatalog
import punishments.common.model.PunishmentReason
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetKind
import punishments.common.model.TargetSelection
import java.util.UUID
import kotlin.time.Instant

object ProtoClientMapper {

    fun CreatePunishmentRequest.toProto(): CreatePunishmentProto = createPunishmentProto {
        type = this@toProto.type.name
        selection = this@toProto.selection.toProto()
        scope = this@toProto.scope.toProto()
        this@toProto.reasonId?.let { reasonId = it }
        this@toProto.reasonText?.let { reasonText = it }
        this@toProto.durationSeconds?.let { durationSeconds = it }
        issuer = this@toProto.issuer.toProto()
        this@toProto.issuedAt?.let { issuedAtEpochMs = it.toEpochMilliseconds() }
    }

    fun RevokePunishmentRequest.toProto(): RevokePunishmentProto = revokePunishmentProto {
        punishmentId = this@toProto.punishmentId.toString()
        this@toProto.reason?.let { reason = it }
        actor = this@toProto.actor.toProto()
    }

    fun GetPunishmentDetailsRequest.toProto(): GetPunishmentDetailsProto = getPunishmentDetailsProto {
        punishmentId = this@toProto.punishmentId.toString()
    }

    fun GetPunishmentsRequest.toProto(): GetPunishmentsProto = getPunishmentsProto {
        targets.addAll(this@toProto.targets.map { target -> target.toProto() })
        this@toProto.type?.let { type = it.name }
        this@toProto.status?.let { status = it.name }
        sort = this@toProto.sort.name
        page = this@toProto.page
        pageSize = this@toProto.pageSize
    }

    fun GetTargetPunishmentsRequest.toProto(): GetTargetPunishmentsProto = getTargetPunishmentsProto {
        targets.addAll(this@toProto.targets.map { target -> target.toProto() })
        page = this@toProto.page
        pageSize = this@toProto.pageSize
    }

    fun SearchPunishmentsRequest.toProto(): SearchPunishmentsProto = searchPunishmentsProto {
        query = this@toProto.query
        page = this@toProto.page
        pageSize = this@toProto.pageSize
    }

    fun GetCatalogRequest.toProto(): GetCatalogProto = getCatalogProto {
        this@toProto.version?.let { version = it }
    }

    fun CreatePunishmentResultProto.toDomain(): CreatePunishmentResult {
        return if (success) {
            CreatePunishmentResult.Success(
                createdIds = createdIdsList.map(UUID::fromString),
                message = message
            )
        } else {
            CreatePunishmentResult.Error(
                code = ErrorCode.valueOf(errorCode),
                message = message
            )
        }
    }

    fun RevokePunishmentResultProto.toDomain(): RevokePunishmentResult {
        return if (success) {
            RevokePunishmentResult.Success(message = message)
        } else {
            RevokePunishmentResult.Error(
                code = ErrorCode.valueOf(errorCode),
                message = message
            )
        }
    }

    fun PunishmentResponseProto.toDomain(): PunishmentResponse? {
        if (!found) {
            return null
        }

        return PunishmentResponse(
            id = UUID.fromString(id),
            type = PunishmentType.valueOf(type),
            status = PunishmentStatus.valueOf(status),
            targets = targetsList.map { target -> target.toDomain() },
            scope = scope.toDomain(),
            reasonId = optionalString(hasReasonId(), reasonId),
            reasonText = optionalString(hasReasonText(), reasonText),
            issuedBy = issuedBy.toDomain(),
            issuedAt = Instant.fromEpochMilliseconds(issuedAtEpochMs),
            expiresAt = if (hasExpiresAtEpochMs()) Instant.fromEpochMilliseconds(expiresAtEpochMs) else null,
            revokedAt = if (hasRevokedAtEpochMs()) Instant.fromEpochMilliseconds(revokedAtEpochMs) else null,
            revokedBy = if (hasRevokedBy()) revokedBy.toDomain() else null
        )
    }

    fun PaginatedPunishmentsProto.toDomain(): PaginatedResponse<PunishmentSummaryResponse> {
        return PaginatedResponse(
            items = itemsList.map { summary -> summary.toDomain() },
            page = page,
            pageSize = pageSize,
            totalItems = totalItems,
            totalPages = totalPages
        )
    }

    fun ReasonCatalogProto.toDomain(): ReasonCatalogResponse {
        return ReasonCatalogResponse(
            catalog = PunishmentCatalog(
                reasons = reasonsList.map { reason -> reason.toDomain() },
                capabilities = capabilitiesList.map { capability -> capability.toDomain() }
            ),
            version = optionalString(hasVersion(), version)
        )
    }

    private fun TargetSelection.toProto(): TargetSelectionProto {
        return TargetSelectionProto.newBuilder()
            .apply {
                this@toProto.selector?.let(::setSelector)
                addAllTargets(this@toProto.targets.map { target -> target.toProto() })
            }
            .build()
    }

    private fun PunishmentTarget.toProto(): PunishmentTargetProto {
        return punishmentTargetProto {
            id = this@toProto.id?.toString().orEmpty()
            name = this@toProto.name.orEmpty()
            kind = this@toProto.kind.name
        }
    }

    private fun PunishmentScope.toProto(): PunishmentScopeProto {
        return punishmentScopeProto {
            restrictionKeys.addAll(this@toProto.restrictionKeys)
        }
    }

    private fun PunishmentActor.toProto(): PunishmentActorProto {
        return punishmentActorProto {
            id = this@toProto.id?.toString().orEmpty()
            name = this@toProto.name
            source = this@toProto.source.name
        }
    }

    private fun PunishmentSummaryProto.toDomain(): PunishmentSummaryResponse {
        return PunishmentSummaryResponse(
            id = UUID.fromString(id),
            type = PunishmentType.valueOf(type),
            status = PunishmentStatus.valueOf(status),
            targets = targetsList.map { target -> target.toDomain() },
            reasonId = optionalString(hasReasonId(), reasonId),
            issuedAt = Instant.fromEpochMilliseconds(issuedAtEpochMs),
            expiresAt = if (hasExpiresAtEpochMs()) Instant.fromEpochMilliseconds(expiresAtEpochMs) else null
        )
    }

    private fun PunishmentTargetProto.toDomain(): PunishmentTarget {
        return PunishmentTarget(
            id = id.uuidOrNull(),
            name = name.takeIf(String::isNotBlank),
            kind = if(kind.isNotBlank()) ActorDto(kind) else TargetKind.UNKNOWN
        )
    }

    private fun PunishmentScopeProto.toDomain(): PunishmentScope {
        return PunishmentScope(restrictionKeysList.toSet())
    }

    private fun PunishmentActorProto.toDomain(): PunishmentActor {
        return PunishmentActor(
            id = id.uuidOrNull(),
            name = name,
            source = if(source.isNotBlank()) ActorDto(source) else ActorSource.SYSTEM
        )
    }

    private fun PunishmentReasonProto.toDomain(): PunishmentReason {
        return PunishmentReason(
            id = id,
            title = title,
            description = optionalString(hasDescription(), description),
            category = optionalString(hasCategory(), category),
            recommendedDurationSeconds = if (hasRecommendedDurationSeconds()) recommendedDurationSeconds else null,
            recommendedScopeKeys = recommendedScopeKeysList.toSet()
        )
    }

    private fun PunishmentCapabilityProto.toDomain(): PunishmentCapability {
        return PunishmentCapability(
            key = key,
            title = title,
            description = optionalString(hasDescription(), description),
            appliesTo = appliesToList.map { value -> PunishmentType.valueOf(value.uppercase()) }.toSet()
        )
    }

    private fun optionalString(hasValue: Boolean, value: String): String? {
        return if (hasValue) value.takeIf(String::isNotBlank) else null
    }

    private inline fun <reified T : Enum<T>> optionalEnum(hasValue: Boolean, value: String): T? {
        return if (hasValue && value.isNotBlank()) enumValueOf(value.uppercase()) else null
    }

    private fun String.uuidOrNull(): UUID? {
        return takeIf(String::isNotBlank)?.let(UUID::fromString)
    }
}
