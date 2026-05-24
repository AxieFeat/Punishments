package punishments.common.dto.response

import kotlinx.serialization.Serializable
import punishments.common.error.ErrorCode
import punishments.common.serialization.UUIDSerializer
import java.util.UUID

/**
 * Result of a create punishment command.
 */
@Serializable
sealed class CreatePunishmentResult {

    @Serializable
    data class Success(
        val createdIds: List<@Serializable(with = UUIDSerializer::class) UUID>,
        val message: String = "Punishment created"
    ) : CreatePunishmentResult()

    @Serializable
    data class Error(val code: ErrorCode, val message: String) : CreatePunishmentResult()
}
