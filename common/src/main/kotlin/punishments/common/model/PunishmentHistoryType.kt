package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Type of lifecycle transition recorded in history.
 */
@Serializable
enum class PunishmentHistoryType {

    CREATED,
    REVOKED,
    EXPIRED,
    UPDATED
}
