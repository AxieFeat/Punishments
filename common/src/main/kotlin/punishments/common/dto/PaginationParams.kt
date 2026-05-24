package punishments.common.dto

import kotlinx.serialization.Serializable
import punishments.common.config.PunishmentDefaults

/**
 * Pageable request parameters used by list endpoints.
 */
@Serializable
data class PaginationParams(
    val page: Int = 0,
    val pageSize: Int = PunishmentDefaults.PAGE_SIZE
) {

    val offset: Long get() = page.toLong() * pageSize
}
