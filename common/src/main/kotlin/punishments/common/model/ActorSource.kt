package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Source of moderation action.
 */
@Serializable
enum class ActorSource {
    STAFF,
    CONSOLE,
    SYSTEM
}
