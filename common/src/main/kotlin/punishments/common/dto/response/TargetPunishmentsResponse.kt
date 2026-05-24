package punishments.common.dto.response

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentRecord

/**
 * Response containing raw punishment records for targets.
 */
@Serializable
data class TargetPunishmentsResponse(
    val punishments: List<PunishmentRecord>
)
