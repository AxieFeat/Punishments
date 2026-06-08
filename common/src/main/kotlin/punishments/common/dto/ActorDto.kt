package punishments.common.dto

import kotlinx.serialization.Serializable
import punishments.common.model.Actor

/**
 * This class created only for deserialization of Actor interface.
 */
@Serializable
data class ActorDto(
    override val name: String
) : Actor
