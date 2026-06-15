package punishments.common.dto.request

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentTarget
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
 *   selection = TargetSelection(
 *      selector = "@a[tag=spam]",
 *      targets = listOf(
 *          PunishmentTarget(
 *              id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
 *              name = "Notch"
 *          ),
 *          PunishmentTarget(
 *              id = UUID.fromString("5d3baa76-5ca3-47eb-8e9e-3d3263b61887"),
 *              name = "nnikitochka"
 *          )
 *      )
 *   ),
 *   scope = PunishmentScope(setOf("chat.text")),
 *   reasonId = "spam",
 *   issuer = PunishmentActor.console()
 * )
 * ```
 *
 * IP-based ban example:
 * ```kotlin
 * CreatePunishmentRequest.forIpAddress(
 *   type = PunishmentType.BAN,
 *   address = "127.0.0.1",
 *   scope = PunishmentScope(setOf("server.join")),
 *   reasonId = "cheat",
 *   issuer = PunishmentActor.console()
 * )
 * ```
 *
 * See also:
 *  - [TargetSelection] For tips about target selection.
 *  - [PunishmentScope] For about punishment scope. Why and how to use it.
 *  - [PunishmentActor] For details about punishment issuer. Custom actors can be created to represent non-player issuers, such as external systems.
 *
 * @property type The type of punishment to create.
 * @property selection The target selection for this punishment. This includes the raw selector string and the resolved targets. Service implementations should use the resolved targets to apply punishments. Your targets list must not be empty.
 * @property scope The scope of the punishment. This defines what actions or permissions are affected by this punishment.
 * For example, a mute punishment might have a scope that includes `chat.text` to indicate that the player cannot send chat messages.
 * If the scope is empty, it should mean the punishment applies to all actions or permissions associated with the punishment type.
 * @property reasonId An optional reason ID for this punishment. This can be used to categorize punishments by predefined reasons. For example, "spam", "griefing", "abuse", etc. In fact is not used by Service implementations, but is included for auditing purposes.
 * This id should ideally come from a predefined catalog of reasons, but it is not strictly required. If provided, it should be a string that uniquely identifies the reason for this punishment.
 * @property reasonText An optional human-readable reason text for this punishment.
 * @property durationSeconds An optional duration in seconds for this punishment. If `null`, the punishment is considered permanent. If provided, the punishment should automatically expire after the specified duration has passed since the [issuedAt] time.
 * @property issuer The actor who issued this punishment. This can be a player, console, or any other entity capable of issuing punishments.
 * @property issuedAt The timestamp when this punishment was issued. This is optional and can be set by the service if not provided. It is used to calculate the expiration time for temporary punishments.
 * @property requestId Optional idempotency key. Reusing the same key with the same request returns the stored command result, which makes client or Envoy retries safe.
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
    val issuedAt: ContextualInstant? = null,
    val requestId: String? = null
) {

    companion object {

        fun forIpAddress(
            type: PunishmentType,
            address: String,
            scope: PunishmentScope = PunishmentScope(),
            reasonId: String? = null,
            reasonText: String? = null,
            durationSeconds: Long? = null,
            issuer: PunishmentActor,
            issuedAt: ContextualInstant? = null,
            requestId: String? = null
        ): CreatePunishmentRequest {
            return CreatePunishmentRequest(
                type = type,
                selection = TargetSelection.ipAddress(address),
                scope = scope,
                reasonId = reasonId,
                reasonText = reasonText,
                durationSeconds = durationSeconds,
                issuer = issuer,
                issuedAt = issuedAt,
                requestId = requestId
            )
        }

        fun forTarget(
            type: PunishmentType,
            target: PunishmentTarget,
            selector: String? = target.name,
            scope: PunishmentScope = PunishmentScope(),
            reasonId: String? = null,
            reasonText: String? = null,
            durationSeconds: Long? = null,
            issuer: PunishmentActor,
            issuedAt: ContextualInstant? = null,
            requestId: String? = null
        ): CreatePunishmentRequest {
            return CreatePunishmentRequest(
                type = type,
                selection = TargetSelection.of(target, selector),
                scope = scope,
                reasonId = reasonId,
                reasonText = reasonText,
                durationSeconds = durationSeconds,
                issuer = issuer,
                issuedAt = issuedAt,
                requestId = requestId
            )
        }
    }
}
