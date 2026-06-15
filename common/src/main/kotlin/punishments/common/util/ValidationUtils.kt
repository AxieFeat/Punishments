package punishments.common.util

import punishments.common.config.PunishmentDefaults
import punishments.common.model.PunishmentTarget
import kotlin.time.Duration

/**
 * Validation helpers for punishment inputs.
 */
object ValidationUtils {

    const val REASON_ID_MAX_LENGTH = 64
    const val REQUEST_ID_MAX_LENGTH = 128
    const val ACTOR_NAME_MAX_LENGTH = 64
    const val TARGET_NAME_MAX_LENGTH = 128
    const val RESTRICTION_KEY_MAX_LENGTH = 128
    const val REASON_TEXT_MAX_LENGTH = 4_096
    const val SEARCH_QUERY_MAX_LENGTH = 256

    fun isValidDuration(duration: Duration): Boolean {
        return duration.isPositive()
    }

    fun isValidDurationSeconds(durationSeconds: Long?): Boolean {
        return durationSeconds == null || durationSeconds > 0
    }

    fun normalizePage(page: Int): Int {
        return page.coerceAtLeast(0)
    }

    fun normalizePageSize(pageSize: Int): Int {
        return pageSize.coerceIn(1, PunishmentDefaults.PAGE_SIZE)
    }

    fun isValidPageSize(pageSize: Int): Boolean {
        return pageSize in 1..PunishmentDefaults.PAGE_SIZE
    }

    fun normalizeReasonId(reasonId: String?): String? {
        return normalizeOptionalString(reasonId, REASON_ID_MAX_LENGTH, "Reason id")
    }

    fun isValidReasonId(reasonId: String?): Boolean {
        return reasonId.isNullOrBlank() || reasonId.trim().length <= REASON_ID_MAX_LENGTH
    }

    fun normalizeReasonText(reasonText: String?): String? {
        return normalizeOptionalString(reasonText, REASON_TEXT_MAX_LENGTH, "Reason text")
    }

    fun normalizeRequestId(requestId: String?): String? {
        return normalizeOptionalString(requestId, REQUEST_ID_MAX_LENGTH, "Request id")
    }

    fun normalizeActorName(name: String): String {
        return normalizeRequiredString(name, ACTOR_NAME_MAX_LENGTH, "Actor name")
    }

    fun normalizeRestrictionKeys(keys: Set<String>): Set<String> {
        return keys.mapNotNull { key ->
            normalizeOptionalString(key, RESTRICTION_KEY_MAX_LENGTH, "Restriction key")
        }.toSet()
    }

    fun normalizeSearchQuery(query: String): String {
        val normalized = query.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
        require(normalized.length <= SEARCH_QUERY_MAX_LENGTH) { "Search query is too long" }
        return normalized
    }

    fun hasUsableTarget(target: PunishmentTarget): Boolean {
        return target.id != null || !target.name.isNullOrBlank()
    }

    fun normalizeTargets(targets: Iterable<PunishmentTarget>): List<PunishmentTarget> {
        return targets.asSequence()
            .filter(::hasUsableTarget)
            .map(::normalizeTarget)
            .distinctBy(TargetKeys::normalized)
            .toList()
    }

    private fun normalizeTarget(target: PunishmentTarget): PunishmentTarget {
        val normalizedName = target.name?.trim()?.takeIf(String::isNotEmpty)
        if (normalizedName != null && normalizedName.length > TARGET_NAME_MAX_LENGTH) {
            throw IllegalArgumentException("Target name is too long")
        }
        return target.copy(name = normalizedName)
    }

    private fun normalizeOptionalString(value: String?, maxLength: Int, label: String): String? {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        require(normalized.length <= maxLength) { "$label is too long" }
        return normalized
    }

    private fun normalizeRequiredString(value: String, maxLength: Int, label: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "$label is required" }
        require(normalized.length <= maxLength) { "$label is too long" }
        return normalized
    }
}
