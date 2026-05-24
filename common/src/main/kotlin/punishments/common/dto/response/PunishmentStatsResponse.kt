package punishments.common.dto.response

import kotlinx.serialization.Serializable

/**
 * Aggregate counters for punishment activity.
 */
@Serializable
data class PunishmentStatsResponse(
    val activePunishments: Int,
    val totalIssued: Int,
    val totalRevoked: Int,
    val totalExpired: Int
)
