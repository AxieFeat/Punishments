package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Target kind for punishment selection.
 */
@Serializable
enum class TargetKind {
    PLAYER,
    ENTITY,
    UNKNOWN
}
