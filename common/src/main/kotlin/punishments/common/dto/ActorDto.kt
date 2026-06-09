package punishments.common.dto

import kotlinx.serialization.Serializable
import punishments.common.model.Actor

/**
 * This class created only for deserialization of Actor interface.
 */
@Serializable
data class ActorDto(
    override val name: String
) : Actor {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Actor) return false

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

}
