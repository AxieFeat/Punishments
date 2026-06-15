package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.ContextualUUID
import java.util.UUID

/**
 * Concrete target resolved from command selection.
 *
 * - See also: [TargetSelection] For details about target selection and how to use it in punishment creation.
 *
 * For IP-based punishments, prefer [ipAddress] or [TargetSelection.ipAddress].
 *
 * @param id The unique identifier of the target, if applicable. For player targets, this would be their UUID. For non-player targets, this can be null.
 * @param name The name of the target. For player targets, this would be their username. For non-player targets, this could be a descriptive name or identifier, such as an IP address or a group name.
 * @param targetType The kind of target, for example [TargetKind.PLAYER] or [TargetKind.IP_ADDRESS].
 */
@Serializable
data class PunishmentTarget(
    val id: ContextualUUID? = null,
    val name: String? = null,
    val targetType: TargetKind = TargetKind.PLAYER
) {

    companion object {
        fun player(id: UUID? = null, name: String? = null): PunishmentTarget {
            return PunishmentTarget(id = id, name = name, targetType = TargetKind.PLAYER)
        }

        fun ipAddress(address: String): PunishmentTarget {
            val normalized = address.trim()
            require(normalized.isNotEmpty()) { "IP address must not be blank" }
            return PunishmentTarget(name = normalized, targetType = TargetKind.IP_ADDRESS)
        }

        fun custom(id: UUID? = null, name: String? = null, targetType: TargetKind): PunishmentTarget {
            return PunishmentTarget(id = id, name = name, targetType = targetType)
        }
    }
}
