package punishments.service.domain.mapper

import punishments.common.dto.response.PunishmentResponse
import punishments.common.dto.response.PunishmentSummaryResponse
import punishments.common.model.PunishmentRecord
import punishments.common.model.PunishmentSummaryRecord

fun PunishmentRecord.toResponse(): PunishmentResponse {
    return PunishmentResponse(
        id = id,
        type = type,
        status = status,
        targets = targets,
        scope = scope,
        reasonId = reasonId,
        reasonText = reasonText,
        issuedBy = issuedBy,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        revokedBy = revokedBy
    )
}

fun PunishmentRecord.toSummary(): PunishmentSummaryResponse {
    return PunishmentSummaryResponse(
        id = id,
        type = type,
        status = status,
        targets = targets,
        reasonId = reasonId,
        reasonText = reasonText,
        issuedAt = issuedAt,
        issuedBy = issuedBy,
        expiresAt = expiresAt
    )
}

fun PunishmentSummaryRecord.toSummary(): PunishmentSummaryResponse {
    return PunishmentSummaryResponse(
        id = id,
        type = type,
        status = status,
        targets = targets,
        reasonId = reasonId,
        reasonText = reasonText,
        issuedAt = issuedAt,
        issuedBy = issuedBy,
        expiresAt = expiresAt
    )
}
