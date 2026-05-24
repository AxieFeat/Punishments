package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Types of punishments supported by the service.
 */
@Serializable
enum class PunishmentType {
    BAN,
    MUTE,
    WARN;

    companion object {
        fun safeValueOf(value: String): PunishmentType? {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}
