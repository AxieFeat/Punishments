package punishments.common.util

import punishments.common.model.PunishmentTarget

/**
 * Human-readable formatter for punishment targets.
 */
object TargetFormatter {

    fun formatTargets(targets: List<PunishmentTarget>): String {
        if (targets.isEmpty()) return "<none>"
        return targets.joinToString(", ") { target ->
            target.name ?: target.id?.toString() ?: "unknown"
        }
    }
}
