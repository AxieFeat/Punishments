package punishments.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import punishments.common.model.ActorSource

object ActorSourceSerializer : KSerializer<ActorSource> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ActorSource", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ActorSource) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): ActorSource {
        return ActorSource.fromSerialized(decoder.decodeString())
    }
}
