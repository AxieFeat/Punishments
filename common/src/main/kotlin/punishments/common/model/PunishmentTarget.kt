package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.UUIDSerializer
import java.util.UUID

/**
 * Concrete target resolved from command selection.
 */
@Serializable
data class PunishmentTarget(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val kind: TargetKind = TargetKind.PLAYER
)
