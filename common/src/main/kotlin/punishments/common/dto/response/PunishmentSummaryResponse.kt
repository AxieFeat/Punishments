package punishments.common.dto.response

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.serialization.ContextualInstant
import punishments.common.serialization.UUIDSerializer
import java.util.UUID

/**
 * Short-form view used in list responses.
 */
@Serializable
data class PunishmentSummaryResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val type: PunishmentType,
    val status: PunishmentStatus,
    val targets: List<PunishmentTarget>,
    val reasonId: String? = null,
    val issuedAt: ContextualInstant,
    val expiresAt: ContextualInstant? = null
)
