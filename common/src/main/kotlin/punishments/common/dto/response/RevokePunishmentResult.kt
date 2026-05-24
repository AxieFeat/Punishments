package punishments.common.dto.response

import kotlinx.serialization.Serializable
import punishments.common.error.ErrorCode

/**
 * Result of a revoke punishment command.
 */
@Serializable
sealed class RevokePunishmentResult {

    @Serializable
    data class Success(val message: String = "Punishment revoked") : RevokePunishmentResult()

    @Serializable
    data class Error(val code: ErrorCode, val message: String) : RevokePunishmentResult()
}
