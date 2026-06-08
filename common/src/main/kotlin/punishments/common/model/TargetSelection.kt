package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Raw selector plus resolved targets for auditing.
 *
 * @param selector The raw selector string. In fact this is not used by Service implementations,
 * but is included for auditing purposes. It may be null if the selector was not provided. In Minecraft this would be something like `@a[tag=spam]`.
 * Or it could be a username, UUID, IP address, or any other identifier.
 * If you want to realize an ip-based punishment system, you could use an ip address as the selector and have a single [PunishmentTarget] with the same ip address.
 *
 * @param targets The resolved targets by [selector]. This is what Service implementations should use to apply punishments. It must not be empty another wise the punishment would not be applied to anyone.
 */
@Serializable
data class TargetSelection(
    val selector: String? = null,
    val targets: List<PunishmentTarget> = emptyList()
)
