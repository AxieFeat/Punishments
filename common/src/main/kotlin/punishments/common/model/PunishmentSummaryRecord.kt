package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.ContextualInstant
import punishments.common.serialization.ContextualUUID

/**
 * Stored short-form representation used by list queries.
 */
@Serializable
data class PunishmentSummaryRecord(
    val id: ContextualUUID,
    val type: PunishmentType,
    val status: PunishmentStatus,
    val targets: List<PunishmentTarget>,
    val reasonId: String? = null,
    val reasonText: String? = null,
    val issuedAt: ContextualInstant,
    val issuedBy: PunishmentActor,
    val expiresAt: ContextualInstant? = null
)
