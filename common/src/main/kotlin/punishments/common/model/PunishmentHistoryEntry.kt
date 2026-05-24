package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.ContextualInstant
import punishments.common.serialization.UUIDSerializer
import java.util.UUID

/**
 * Immutable entry describing a change in punishment lifecycle.
 */
@Serializable
data class PunishmentHistoryEntry(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = UUIDSerializer::class)
    val punishmentId: UUID,
    val type: PunishmentHistoryType,
    val actor: PunishmentActor? = null,
    val note: String? = null,
    val timestamp: ContextualInstant
)
