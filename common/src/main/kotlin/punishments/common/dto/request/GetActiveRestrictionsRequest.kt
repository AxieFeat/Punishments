package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType

/**
 * Strict read request for all active restrictions currently affecting targets.
 */
@Serializable
data class GetActiveRestrictionsRequest(
    val targets: List<PunishmentTarget>,
    val types: Set<PunishmentType> = emptySet()
)
