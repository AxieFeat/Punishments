package punishments.service.grpc.mapper

import io.grpc.Status
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
import punishments.common.error.PunishmentException
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
import punishments.common.grpc.createPunishmentResultProto
import punishments.common.grpc.paginatedPunishmentsProto
import punishments.common.grpc.punishmentActorProto
import punishments.common.grpc.punishmentCapabilityProto
import punishments.common.grpc.punishmentReasonProto
import punishments.common.grpc.punishmentResponseProto
import punishments.common.grpc.punishmentScopeProto
import punishments.common.grpc.punishmentSummaryProto
import punishments.common.grpc.punishmentTargetProto
import punishments.common.grpc.reasonCatalogProto
import punishments.common.grpc.revokePunishmentResultProto
import punishments.common.model.ActorSourceType
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentCapability
import punishments.common.model.PunishmentReason
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentSort
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetType
import punishments.common.model.TargetSelection
import punishments.common.dto.ActorTypeDto
import java.util.UUID
import kotlin.time.Instant

object ProtoMapper {

    fun CreatePunishmentProto.toDomain(): CreatePunishmentRequest {
        return CreatePunishmentRequest(
            type = enumValue(type, "type"),
            selection = if (hasSelection()) selection.toDomain() else TargetSelection(),
            scope = if (hasScope()) scope.toDomain() else PunishmentScope(),
            reasonId = optionalString(hasReasonId(), reasonId),
            reasonText = optionalString(hasReasonText(), reasonText),
            durationSeconds = if (hasDurationSeconds()) durationSeconds else null,
            issuer = if (hasIssuer()) issuer.toDomain() else missingActor(),
            issuedAt = if (hasIssuedAtEpochMs()) Instant.fromEpochMilliseconds(issuedAtEpochMs) else null
        )
    }

    fun RevokePunishmentProto.toDomain(): RevokePunishmentRequest {
        return RevokePunishmentRequest(
            punishmentId = UUID.fromString(punishmentId),
            reason = optionalString(hasReason(), reason),
            actor = if (hasActor()) actor.toDomain() else missingActor()
        )
    }

    fun GetPunishmentDetailsProto.toDomain(): GetPunishmentDetailsRequest {
        return GetPunishmentDetailsRequest(UUID.fromString(punishmentId))
    }

    fun GetPunishmentsProto.toDomain(): GetPunishmentsRequest {
        return GetPunishmentsRequest(
            targets = targetsList.map { target -> target.toDomain() },
            type = optionalEnum<PunishmentType>(hasType(), type),
            status = optionalEnum<PunishmentStatus>(hasStatus(), status),
            sort = optionalEnum<PunishmentSort>(hasSort(), sort) ?: PunishmentSort.NEWEST,
            page = page,
            pageSize = pageSize
        )
    }

    fun GetTargetPunishmentsProto.toDomain(): GetTargetPunishmentsRequest {
        return GetTargetPunishmentsRequest(
            targets = targetsList.map { target -> target.toDomain() },
            page = page,
            pageSize = pageSize
        )
    }

    fun SearchPunishmentsProto.toDomain(): SearchPunishmentsRequest {
        return SearchPunishmentsRequest(
            query = query,
            page = page,
            pageSize = pageSize
        )
    }

    fun GetCatalogProto.toDomain(): GetCatalogRequest {
        return GetCatalogRequest(version = optionalString(hasVersion(), version))
    }

    fun CreatePunishmentResult.toProto(): CreatePunishmentResultProto {
        return when (this) {
            is CreatePunishmentResult.Success -> createPunishmentResultProto {
                success = true
                createdIds.addAll(this@toProto.createdIds.map(UUID::toString))
                message = this@toProto.message
            }
            is CreatePunishmentResult.Error -> createPunishmentResultProto {
                success = false
                errorCode = this@toProto.code.name
                message = this@toProto.message
            }
        }
    }

    fun RevokePunishmentResult.toProto(): RevokePunishmentResultProto {
        return when (this) {
            is RevokePunishmentResult.Success -> revokePunishmentResultProto {
                success = true
                message = this@toProto.message
            }
            is RevokePunishmentResult.Error -> revokePunishmentResultProto {
                success = false
                errorCode = this@toProto.code.name
                message = this@toProto.message
            }
        }
    }

    fun PunishmentResponse?.toProto(): PunishmentResponseProto {
        val response = this ?: return punishmentResponseProto { found = false }
        return punishmentResponseProto {
            found = true
            id = response.id.toString()
            type = response.type.name
            status = response.status.name
            targets.addAll(response.targets.map { target -> target.toProto() })
            scope = response.scope.toProto()
            response.reasonId?.let { reasonId = it }
            response.reasonText?.let { reasonText = it }
            issuedBy = response.issuedBy.toProto()
            issuedAtEpochMs = response.issuedAt.toEpochMilliseconds()
            response.expiresAt?.let { expiresAtEpochMs = it.toEpochMilliseconds() }
            response.revokedAt?.let { revokedAtEpochMs = it.toEpochMilliseconds() }
            response.revokedBy?.let { revokedBy = it.toProto() }
        }
    }

    fun PaginatedResponse<PunishmentSummaryResponse>.toProto(): PaginatedPunishmentsProto {
        return paginatedPunishmentsProto {
            items.addAll(this@toProto.items.map { summary -> summary.toProto() })
            page = this@toProto.page
            pageSize = this@toProto.pageSize
            totalItems = this@toProto.totalItems
            totalPages = this@toProto.totalPages
        }
    }

    fun ReasonCatalogResponse.toProto(): ReasonCatalogProto {
        return reasonCatalogProto {
            reasons.addAll(catalog.reasons.map { reason -> reason.toProto() })
            capabilities.addAll(catalog.capabilities.map { capability -> capability.toProto() })
            this@toProto.version?.let { version = it }
        }
    }

    fun PunishmentException.toGrpcStatus(): Status {
        return when (errorCode) {
            ErrorCode.PUNISHMENT_NOT_FOUND,
            ErrorCode.TARGET_NOT_FOUND,
            ErrorCode.REASON_NOT_FOUND -> Status.NOT_FOUND
            ErrorCode.PUNISHMENT_ALREADY_REVOKED,
            ErrorCode.PUNISHMENT_ALREADY_ACTIVE -> Status.ALREADY_EXISTS
            ErrorCode.INVALID_SCOPE,
            ErrorCode.INVALID_REQUEST -> Status.INVALID_ARGUMENT
            ErrorCode.INTERNAL_ERROR -> Status.INTERNAL
        }.withDescription(message)
    }

    private fun TargetSelectionProto.toDomain(): TargetSelection {
        return TargetSelection(
            selector = optionalString(hasSelector(), selector),
            targets = targetsList.map { target -> target.toDomain() }
        )
    }

    private fun PunishmentTargetProto.toDomain(): PunishmentTarget {
        return PunishmentTarget(
            id = id.uuidOrNull(),
            name = name.takeIf(String::isNotBlank),
            kind = if(kind.isNotBlank()) ActorTypeDto(kind) else TargetType.UNKNOWN
        )
    }

    private fun PunishmentScopeProto.toDomain(): PunishmentScope {
        return PunishmentScope(restrictionKeysList.toSet())
    }

    private fun PunishmentActorProto.toDomain(): PunishmentActor {
        return PunishmentActor(
            id = id.uuidOrNull(),
            name = name,
            source = if(source.isNotBlank()) ActorTypeDto(source) else ActorSourceType.SYSTEM
        )
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

    private fun PunishmentSummaryResponse.toProto(): PunishmentSummaryProto {
        return punishmentSummaryProto {
            id = this@toProto.id.toString()
            type = this@toProto.type.name
            status = this@toProto.status.name
            targets.addAll(this@toProto.targets.map { target -> target.toProto() })
            this@toProto.reasonId?.let { reasonId = it }
            this@toProto.reasonText?.let { reasonText = it }
            issuedAtEpochMs = this@toProto.issuedAt.toEpochMilliseconds()
            issuedBy = this@toProto.issuedBy.toProto()
            this@toProto.expiresAt?.let { expiresAtEpochMs = it.toEpochMilliseconds() }
        }
    }

    private fun PunishmentReason.toProto(): PunishmentReasonProto {
        return punishmentReasonProto {
            id = this@toProto.id
            title = this@toProto.title
            this@toProto.description?.let { description = it }
            this@toProto.category?.let { category = it }
            this@toProto.recommendedDurationSeconds?.let { recommendedDurationSeconds = it }
            recommendedScopeKeys.addAll(this@toProto.recommendedScopeKeys)
        }
    }

    private fun PunishmentCapability.toProto(): PunishmentCapabilityProto {
        return punishmentCapabilityProto {
            key = this@toProto.key
            title = this@toProto.title
            this@toProto.description?.let { description = it }
            appliesTo.addAll(this@toProto.appliesTo.map(PunishmentType::name))
        }
    }

    private fun optionalString(hasValue: Boolean, value: String): String? {
        return if (hasValue) value.takeIf(String::isNotBlank) else null
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String, field: String): T {
        if (value.isBlank()) {
            throw IllegalArgumentException("$field is required")
        }
        return enumValueOf(value.uppercase())
    }

    private inline fun <reified T : Enum<T>> optionalEnum(hasValue: Boolean, value: String): T? {
        return if (hasValue && value.isNotBlank()) enumValueOf(value.uppercase()) else null
    }

    private fun String.uuidOrNull(): UUID? {
        return takeIf(String::isNotBlank)?.let(UUID::fromString)
    }

    private fun missingActor(): PunishmentActor {
        throw IllegalArgumentException("actor is required")
    }
}
