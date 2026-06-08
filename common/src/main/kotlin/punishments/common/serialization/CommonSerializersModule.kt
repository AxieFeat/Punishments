package punishments.common.serialization

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import punishments.common.model.Actor
import java.util.UUID
import kotlin.time.Instant

/**
 * Central serializers module shared by common DTOs.
 */
val CommonSerializersModule: SerializersModule = SerializersModule {
    contextual(Instant::class, InstantSerializer)
    contextual(UUID::class, UUIDSerializer)
    contextual(Actor::class, ActorSerializer)
}
