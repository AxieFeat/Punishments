package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentActor
import punishments.common.serialization.UUIDSerializer
import java.util.UUID

/**
 * Request to revoke an active punishment.
 */
@Serializable
data class RevokePunishmentRequest(
    @Serializable(with = UUIDSerializer::class)
    val punishmentId: UUID,
    val reason: String? = null,
    val actor: PunishmentActor
)
