package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Raw selector plus resolved targets for auditing.
 */
@Serializable
data class TargetSelection(
    val selector: String? = null,
    val targets: List<PunishmentTarget> = emptyList()
)
