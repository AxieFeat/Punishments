package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.serialization.ContextualUUID

/**
 * Request to fetch a punishment by id.
 */
@Serializable
data class GetPunishmentDetailsRequest(
    val punishmentId: ContextualUUID
)
