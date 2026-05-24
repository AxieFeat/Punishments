package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection
import punishments.common.serialization.ContextualInstant

/**
 * Request to create a punishment for one or more targets.
 *
 * Example:
 * ```kotlin
 * CreatePunishmentRequest(
 *   type = PunishmentType.MUTE,
 *   selection = TargetSelection(selector = "@a[tag=spam]"),
 *   scope = PunishmentScope(setOf("chat.text")),
 *   reasonId = "spam",
 *   issuer = PunishmentActor(name = "Console", source = ActorSource.CONSOLE)
 * )
 * ```
 */
@Serializable
data class CreatePunishmentRequest(
    val type: PunishmentType,
    val selection: TargetSelection,
    val scope: PunishmentScope = PunishmentScope(),
    val reasonId: String? = null,
    val reasonText: String? = null,
    val durationSeconds: Long? = null,
    val issuer: PunishmentActor,
    val issuedAt: ContextualInstant? = null
)
