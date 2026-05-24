package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.serialization.UUIDSerializer
import java.util.UUID

/**
 * Request to fetch a punishment by id.
 */
@Serializable
data class GetPunishmentRequest(
    @Serializable(with = UUIDSerializer::class)
    val punishmentId: UUID
)
