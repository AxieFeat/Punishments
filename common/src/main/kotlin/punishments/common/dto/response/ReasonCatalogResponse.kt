package punishments.common.dto.response

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentCatalog

/**
 * Response wrapper for built-in reason catalog.
 */
@Serializable
data class ReasonCatalogResponse(
    val catalog: PunishmentCatalog,
    val version: String? = null
)
