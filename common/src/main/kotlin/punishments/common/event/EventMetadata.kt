package punishments.common.event

import kotlinx.serialization.Serializable
import punishments.common.serialization.ContextualInstant
import punishments.common.serialization.ContextualUUID
import java.util.UUID
import kotlin.time.Instant

/**
 * Envelope metadata attached to domain events.
 */
@Serializable
data class EventMetadata(
    val eventId: ContextualUUID = UUID.randomUUID(),
    val timestamp: ContextualInstant = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
    val sourceServer: String = "unknown"
)
