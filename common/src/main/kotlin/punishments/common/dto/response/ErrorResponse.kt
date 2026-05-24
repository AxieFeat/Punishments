package punishments.common.dto.response

import kotlinx.serialization.Serializable
import punishments.common.error.ErrorCode

/**
 * Standard error payload for HTTP transport.
 */
@Serializable
data class ErrorResponse(
    val code: ErrorCode,
    val message: String
)
