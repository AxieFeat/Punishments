package punishments.common.dto.response

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.serialization.ContextualInstant
import punishments.common.serialization.ContextualUUID

@Serializable
data class ActiveRestrictionResponse(
    val punishmentId: ContextualUUID,
    val type: PunishmentType,
    val target: PunishmentTarget,
    val restrictionKeys: Set<String>,
    val reasonId: String? = null,
    val expiresAt: ContextualInstant? = null
)
