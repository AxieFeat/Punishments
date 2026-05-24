package punishments.common.serialization

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.time.Instant

/**
 * Central serializers module shared by common DTOs.
 */
val CommonSerializersModule: SerializersModule = SerializersModule {
    contextual(Instant::class, InstantSerializer)
}
