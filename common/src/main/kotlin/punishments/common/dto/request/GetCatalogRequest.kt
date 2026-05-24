package punishments.common.dto.request

import kotlinx.serialization.Serializable

/**
 * Request to fetch the built-in reason catalog.
 */
@Serializable
data class GetCatalogRequest(
    val version: String? = null
)
