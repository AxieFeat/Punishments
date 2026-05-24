package punishments.common.error

/**
 * Base exception for punishment domain errors.
 */
sealed class PunishmentException(
    val errorCode: ErrorCode,
    override val message: String
) : Exception(message)

/**
 * Raised when a punishment id cannot be resolved.
 */
class PunishmentNotFoundException(punishmentId: String) :
    PunishmentException(ErrorCode.PUNISHMENT_NOT_FOUND, "Punishment not found: $punishmentId")

/**
 * Raised when a target reference cannot be resolved.
 */
class TargetNotFoundException(target: String) :
    PunishmentException(ErrorCode.TARGET_NOT_FOUND, "Target not found: $target")

/**
 * Raised when a punishment is already revoked.
 */
class PunishmentAlreadyRevokedException(punishmentId: String) :
    PunishmentException(ErrorCode.PUNISHMENT_ALREADY_REVOKED, "Punishment already revoked: $punishmentId")

/**
 * Raised when an active punishment is created or activated again.
 */
class PunishmentAlreadyActiveException(punishmentId: String) :
    PunishmentException(ErrorCode.PUNISHMENT_ALREADY_ACTIVE, "Punishment already active: $punishmentId")

/**
 * Raised when a built-in reason id is missing in the catalog.
 */
class ReasonNotFoundException(reasonId: String) :
    PunishmentException(ErrorCode.REASON_NOT_FOUND, "Reason not found: $reasonId")

/**
 * Raised when a scope key is not supported by the catalog.
 */
class InvalidScopeException(scope: String) :
    PunishmentException(ErrorCode.INVALID_SCOPE, "Invalid scope: $scope")
