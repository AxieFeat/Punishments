package punishments.common.serialization

import kotlinx.serialization.Contextual
import punishments.common.model.ActorType
import java.util.UUID
import kotlin.time.Instant

/**
 * Instant serialized via a contextual module.
 */
typealias ContextualInstant = @Contextual Instant

/**
 * UUID serialized via a contextual module.
 */
typealias ContextualUUID = @Contextual UUID

/**
 * Actor serialized via a contextual module.
 */
typealias ContextualActor = @Contextual ActorType
