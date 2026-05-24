package punishments.common.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueType
import punishments.common.model.PunishmentCatalog
import punishments.common.model.PunishmentCapability
import punishments.common.model.PunishmentReason
import punishments.common.model.PunishmentType

/**
 * Loads the built-in punishment catalog from HOCON configuration.
 *
 * Example (fragment of `punishments.conf`):
 * ```
 * punishments {
 *   capabilities = [{ key = "chat.text", title = "Text chat", appliesTo = ["MUTE"] }]
 *   reasons = [{ id = "spam", title = "Spam", recommendedScopeKeys = ["chat.text"] }]
 * }
 * ```
 */
object PunishmentCatalogLoader {

    fun load(resource: String = PunishmentDefaults.CATALOG_RESOURCE): PunishmentCatalog {
        val config = ConfigFactory.parseResources(resource).withFallback(ConfigFactory.load())
        return parse(config)
    }

    fun parse(config: Config): PunishmentCatalog {
        val root = config.getConfig("punishments")
        val capabilities = root.getConfigList("capabilities").map { entry ->
            PunishmentCapability(
                key = entry.getString("key"),
                title = entry.getString("title"),
                description = entry.optionalString("description"),
                appliesTo = entry.optionalStringList("appliesTo")
                    .mapNotNull { PunishmentType.safeValueOf(it) }
                    .toSet()
            )
        }
        val reasons = root.getConfigList("reasons").map { entry ->
            PunishmentReason(
                id = entry.getString("id"),
                title = entry.getString("title"),
                description = entry.optionalString("description"),
                category = entry.optionalString("category"),
                recommendedDurationSeconds = entry.optionalLong("recommendedDurationSeconds"),
                recommendedScopeKeys = entry.optionalStringList("recommendedScopeKeys").toSet()
            )
        }
        return PunishmentCatalog(reasons = reasons, capabilities = capabilities)
    }

    private fun Config.optionalString(path: String): String? {
        return if (hasPath(path) && getValue(path).valueType() != ConfigValueType.NULL) getString(path) else null
    }

    private fun Config.optionalLong(path: String): Long? {
        return if (hasPath(path) && getValue(path).valueType() != ConfigValueType.NULL) getLong(path) else null
    }

    private fun Config.optionalStringList(path: String): List<String> {
        return if (hasPath(path) && getValue(path).valueType() != ConfigValueType.NULL) getStringList(path) else emptyList()
    }
}
