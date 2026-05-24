package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Supported sorting modes for punishment listings.
 */
@Serializable
enum class PunishmentSort {
    NEWEST,
    OLDEST,
    EXPIRES_SOON
}
