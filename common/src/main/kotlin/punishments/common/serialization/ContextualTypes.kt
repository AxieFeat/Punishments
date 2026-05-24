package punishments.common.serialization

import kotlinx.serialization.Contextual
import kotlin.time.Instant

/**
 * Instant serialized via a contextual module.
 */
typealias ContextualInstant = @Contextual Instant
