package punishments.common.dto.response

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.serialization.ContextualInstant
import punishments.common.serialization.ContextualUUID

/**
 * Detailed representation of a punishment for read endpoints.
 */
@Serializable
data class PunishmentResponse(
    val id: ContextualUUID,
    val type: PunishmentType,
    val status: PunishmentStatus,
    val targets: List<PunishmentTarget>,
    val scope: PunishmentScope,
    val reasonId: String? = null,
    val reasonText: String? = null,
    val issuedBy: PunishmentActor,
    val issuedAt: ContextualInstant,
    val expiresAt: ContextualInstant? = null,
    val revokedAt: ContextualInstant? = null,
    val revokedBy: PunishmentActor? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PunishmentResponse) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
