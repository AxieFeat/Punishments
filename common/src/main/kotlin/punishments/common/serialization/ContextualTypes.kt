package punishments.common.serialization

import kotlinx.serialization.Contextual
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
