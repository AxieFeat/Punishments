package punishments.common.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class TargetRestrictionsResponse(
    val restricted: Boolean,
    val restrictions: List<ActiveRestrictionResponse>
)
