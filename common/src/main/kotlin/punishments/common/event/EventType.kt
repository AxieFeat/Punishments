package punishments.common.event

import kotlinx.serialization.Serializable

/**
 * High-level event types for punishments.
 */
@Serializable
enum class EventType {

    PUNISHMENT_CREATED,
    PUNISHMENT_REVOKED,
    PUNISHMENT_EXPIRED
}
