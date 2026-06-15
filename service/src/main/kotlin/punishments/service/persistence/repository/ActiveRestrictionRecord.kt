package punishments.service.persistence.repository

import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import java.util.UUID

data class ActiveRestrictionRecord(
    val punishmentId: UUID,
    val type: PunishmentType,
    val target: PunishmentTarget,
    val restrictionKeys: Set<String>,
    val reasonId: String?,
    val expiresAtEpochMs: Long?
)
