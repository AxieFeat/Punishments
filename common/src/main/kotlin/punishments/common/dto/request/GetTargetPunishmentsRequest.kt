package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.config.PunishmentDefaults
import punishments.common.model.PunishmentTarget

/**
 * Request to list punishments for specific targets.
 */
@Serializable
data class GetTargetPunishmentsRequest(
    val targets: List<PunishmentTarget>,
    val page: Int = 0,
    val pageSize: Int = PunishmentDefaults.PAGE_SIZE
)
