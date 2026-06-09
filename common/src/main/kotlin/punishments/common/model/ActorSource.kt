package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Source of moderation action. Our implementation provides only three sources.
 * For custom implementation you can create your own sources by implementing [Actor] interface and using it in [PunishmentActor.source] property.
 *
 * @see Actor
 */
@Serializable
enum class ActorSource : Actor {

    /**
     * Represents a staff member or moderator who issued the punishment.
     * This is the default source for player-issued punishments.
     */
    STAFF,

    /**
     * Represents the console or server itself as the source of the punishment.
     * This is typically used for punishments issued by server commands.
     */
    CONSOLE,

    /**
     * Represents an external system or plugin as the source of the punishment.
     * This can be used for punishments issued by third-party integrations, such as a web dashboard or an anti-cheat system.
     */
    SYSTEM
}
