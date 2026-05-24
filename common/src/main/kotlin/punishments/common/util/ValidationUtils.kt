package punishments.common.util

import punishments.common.config.PunishmentDefaults
import kotlin.time.Duration

/**
 * Validation helpers for punishment inputs.
 */
object ValidationUtils {

    fun isValidDuration(duration: Duration): Boolean {
        return duration.isPositive()
    }

    fun isValidPageSize(pageSize: Int): Boolean {
        return pageSize in 1..PunishmentDefaults.PAGE_SIZE
    }

    fun isValidReasonId(reasonId: String?): Boolean {
        return reasonId.isNullOrBlank() || reasonId.length <= 64
    }
}
