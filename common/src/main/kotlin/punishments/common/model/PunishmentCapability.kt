package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Describes a restriction key that can be applied by punishments.
 */
@Serializable
data class PunishmentCapability(
    val key: String,
    val title: String,
    val description: String? = null,
    val appliesTo: Set<PunishmentType> = emptySet()
)
