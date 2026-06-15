package punishments.common.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Raw selector plus resolved targets for auditing.
 *
 * @param selector The raw selector string. In fact this is not used by Service implementations,
 * but is included for auditing purposes. It may be null if the selector was not provided. In Minecraft this would be something like `@a[tag=spam]`.
 * Or it could be a username, UUID, IP address, or any other identifier.
 *
 * @param targets The targets resolved from [selector]. Service implementations should use these targets to apply punishments. The list must not be empty.
 */
@Serializable
data class TargetSelection(
    val selector: String? = null,
    val targets: List<PunishmentTarget> = emptyList()
) {

    companion object {

        fun of(target: PunishmentTarget, selector: String? = null): TargetSelection {
            return TargetSelection(selector = selector, targets = listOf(target))
        }

        fun of(targets: Iterable<PunishmentTarget>, selector: String? = null): TargetSelection {
            return TargetSelection(selector = selector, targets = targets.toList())
        }

        fun player(id: UUID? = null, name: String? = null, selector: String? = name): TargetSelection {
            return of(PunishmentTarget.player(id = id, name = name), selector = selector)
        }

        fun ipAddress(address: String): TargetSelection {
            val normalized = address.trim()
            require(normalized.isNotEmpty()) { "IP address must not be blank" }
            return of(PunishmentTarget.ipAddress(normalized), selector = normalized)
        }

        fun ipAddresses(addresses: Iterable<String>, selector: String? = null): TargetSelection {
            val targets = addresses.map { address ->
                val normalized = address.trim()
                require(normalized.isNotEmpty()) { "IP address must not be blank" }
                PunishmentTarget.ipAddress(normalized)
            }
            return TargetSelection(selector = selector, targets = targets)
        }
    }
}
