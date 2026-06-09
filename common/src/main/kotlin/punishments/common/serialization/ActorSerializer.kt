package punishments.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import punishments.common.dto.ActorTypeDto
import punishments.common.model.ActorType

/**
 * Serializes Kotlin durations as seconds.
 */
object ActorSerializer : KSerializer<ActorType> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Actor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ActorType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): ActorType {
        return ActorTypeDto(decoder.decodeString())
    }
}
