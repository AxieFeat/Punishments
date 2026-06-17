package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.ContextualInstant
import punishments.common.serialization.ContextualUUID

/**
 * Stored representation of a punishment with lifecycle timestamps.
 */
@Serializable
data class PunishmentRecord(
    val id: ContextualUUID,
    val type: PunishmentType,
    val status: PunishmentStatus,
    val targets: List<PunishmentTarget>,
    val targetSelector: String? = null,
    val scope: PunishmentScope = PunishmentScope(),
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
        if (other !is PunishmentRecord) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
