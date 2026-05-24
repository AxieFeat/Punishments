package punishments.common.dto.response

import kotlinx.serialization.Serializable
import punishments.common.error.ErrorCode

/**
 * Generic success/error wrapper for simple commands.
 */
@Serializable
sealed class ActionResult {

    @Serializable
    data class Success(val message: String) : ActionResult()

    @Serializable
    data class Error(val code: ErrorCode, val message: String) : ActionResult()
}
