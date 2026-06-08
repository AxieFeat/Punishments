package punishments.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import punishments.common.dto.ActorDto
import punishments.common.model.Actor

/**
 * Serializes Kotlin durations as seconds.
 */
object ActorSerializer : KSerializer<Actor> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Actor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Actor) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): Actor {
        return ActorDto(decoder.decodeString())
    }
}
