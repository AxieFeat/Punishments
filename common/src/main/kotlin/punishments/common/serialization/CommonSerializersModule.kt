package punishments.common.serialization

import kotlinx.serialization.modules.SerializersModule
import punishments.common.model.ActorType
import java.util.UUID
import kotlin.time.Instant

/**
 * Central serializers module shared by common DTOs.
 */
val CommonSerializersModule: SerializersModule = SerializersModule {
    contextual(Instant::class, InstantSerializer)
    contextual(UUID::class, UUIDSerializer)
    contextual(ActorType::class, ActorSerializer)
}
