package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType

/**
 * Strict enforcement request for checking whether targets have active restrictions.
 *
 * This API is intentionally separate from paginated history/listing APIs because
 * enforcement must not consume bounded-stale list/search cache entries.
 */
@Serializable
data class CheckTargetRestrictionsRequest(
    val targets: List<PunishmentTarget>,
    val restrictionKeys: Set<String> = emptySet(),
    val types: Set<PunishmentType> = emptySet()
)
