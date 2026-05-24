package punishments.common.error

import kotlinx.serialization.Serializable

/**
 * Error codes used across punishment APIs and transports.
 */
@Serializable
enum class ErrorCode(val httpStatus: Int) {

    PUNISHMENT_NOT_FOUND(404),
    TARGET_NOT_FOUND(404),
    PUNISHMENT_ALREADY_REVOKED(409),
    PUNISHMENT_ALREADY_ACTIVE(409),
    REASON_NOT_FOUND(404),
    INVALID_SCOPE(400),
    INVALID_REQUEST(400),
    INTERNAL_ERROR(500)
}
