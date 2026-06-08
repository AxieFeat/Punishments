package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.ContextualInstant
import punishments.common.serialization.ContextualUUID
import java.util.UUID

/**
 * Immutable entry describing a change in punishment lifecycle.
 */
@Serializable
data class PunishmentHistoryEntry(
    val id: ContextualUUID = UUID.randomUUID(),
    val punishmentId: ContextualUUID,
    val type: PunishmentHistoryType,
    val actor: PunishmentActor? = null,
    val note: String? = null,
    val timestamp: ContextualInstant
)
