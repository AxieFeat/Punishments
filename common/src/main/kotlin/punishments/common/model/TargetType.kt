package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Target kind for punishment selection. Our implementation provides only three target kinds.
 * For custom implementation you can create your own sources by implementing [ActorType] interface and using it in [PunishmentTarget.kind] property.
 *
 * @see ActorType
 */
@Serializable
enum class TargetType : ActorType {

    /**
     * Represents a player as the target of the punishment. This is the most common target kind for punishments, such as bans, mutes, or warnings issued to individual players.
     */
    PLAYER,

    /**
     * Represents any non-player target.
     * In example, it can be an IP address as the target of the punishment.
     */
    UNKNOWN
}
