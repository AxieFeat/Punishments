package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.ContextualActor
import punishments.common.serialization.ContextualUUID

/**
 * Concrete target resolved from command selection.
 *
 * See also: [TargetSelection] For details about target selection and how to use it in punishment creation.
 *
 * For ip-based punishment, you can use an ip address as the [name].
 * In this case, the [id] can be `null` and the [kind] can be set to [TargetKind.UNKNOWN] or a custom target kind if you have defined one.
 *
 * @param id The unique identifier of the target, if applicable. For player targets, this would be their UUID. For non-player targets, this can be null.
 * @param name The name of the target. For player targets, this would be their username. For non-player targets, this could be a descriptive name or identifier, such as an IP address or a group name.
 * @param kind The kind of target, which indicates whether the target is a player or ip in example.
 */
@Serializable
data class PunishmentTarget(
    val id: ContextualUUID? = null,
    val name: String? = null,
    val kind: ContextualActor = TargetKind.PLAYER
)
