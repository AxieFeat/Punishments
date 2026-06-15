package punishments.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import punishments.common.model.TargetKind

object TargetKindSerializer : KSerializer<TargetKind> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TargetKind", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TargetKind) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): TargetKind {
        return TargetKind.fromSerialized(decoder.decodeString())
    }
}
