package punishments.common.dto.response

import kotlinx.serialization.Serializable

/**
 * Generic container for paginated results.
 */
@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int
)
