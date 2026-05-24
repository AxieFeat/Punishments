package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Current lifecycle status of a punishment.
 */
@Serializable
enum class PunishmentStatus {
    ACTIVE,
    EXPIRED,
    REVOKED
}
