package punishments.common.event

import kotlinx.serialization.Serializable
import punishments.common.serialization.ContextualInstant
import punishments.common.serialization.UUIDSerializer
import java.util.UUID
import kotlin.time.Instant

/**
 * Envelope metadata attached to domain events.
 */
@Serializable
data class EventMetadata(
    @Serializable(with = UUIDSerializer::class)
    val eventId: UUID = UUID.randomUUID(),
    val timestamp: ContextualInstant = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
    val sourceServer: String = "unknown"
)
