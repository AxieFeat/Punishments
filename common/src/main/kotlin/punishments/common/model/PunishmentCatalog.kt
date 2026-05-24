package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Collection of built-in reasons and restriction capabilities.
 */
@Serializable
data class PunishmentCatalog(
    val reasons: List<PunishmentReason> = emptyList(),
    val capabilities: List<PunishmentCapability> = emptyList()
) {
    fun reasonById(reasonId: String): PunishmentReason? = reasons.firstOrNull { it.id == reasonId }
}
