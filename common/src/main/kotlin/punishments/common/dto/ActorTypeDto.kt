package punishments.common.dto

import kotlinx.serialization.Serializable
import punishments.common.model.ActorType

/**
 * This class created only for deserialization of [ActorType] interface.
 */
@Serializable
data class ActorTypeDto(
    override val name: String
) : ActorType {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActorType) return false

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

}
