package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Built-in reason descriptor used for analytics and UI hints.
 */
@Serializable
data class PunishmentReason(
    val id: String,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val recommendedDurationSeconds: Long? = null,
    val recommendedScopeKeys: Set<String> = emptySet()
)
