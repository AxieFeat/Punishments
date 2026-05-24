package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.config.PunishmentDefaults
import punishments.common.model.PunishmentSort
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType

/**
 * Request to list punishments with filters and paging.
 */
@Serializable
data class GetPunishmentsRequest(
    val targets: List<PunishmentTarget> = emptyList(),
    val type: PunishmentType? = null,
    val status: PunishmentStatus? = null,
    val sort: PunishmentSort = PunishmentSort.NEWEST,
    val page: Int = 0,
    val pageSize: Int = PunishmentDefaults.PAGE_SIZE
)
