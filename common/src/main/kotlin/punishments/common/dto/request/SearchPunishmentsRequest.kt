package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.config.PunishmentDefaults

/**
 * Request to search punishments by text query.
 */
@Serializable
data class SearchPunishmentsRequest(
    val query: String,
    val page: Int = 0,
    val pageSize: Int = PunishmentDefaults.PAGE_SIZE
)
