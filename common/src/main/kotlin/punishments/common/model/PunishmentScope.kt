package punishments.common.model

import kotlinx.serialization.Serializable

/**
 * Explicit restriction keys applied by a punishment.
 *
 * Example: `restrictionKeys = setOf("chat.text", "chat.voice")`.
 */
@Serializable
data class PunishmentScope(
    val restrictionKeys: Set<String> = emptySet()
)
