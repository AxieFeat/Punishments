package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentActor
import punishments.common.serialization.ContextualUUID

/**
 * Request to revoke an active punishment.
 *
 * @property punishmentId Unique identifier of the punishment to revoke. You can receive it via [GetPunishmentsRequest] or [GetTargetPunishmentsRequest] by providing some identifier of target.
 * @property reason Optional reason for revoking the punishment. This can be used for auditing purposes.
 * @property actor The actor who is revoking the punishment. This can be a player, console, or any other entity capable of revoking punishments.
 */
@Serializable
data class RevokePunishmentRequest(
    val punishmentId: ContextualUUID,
    val reason: String? = null,
    val actor: PunishmentActor
)
