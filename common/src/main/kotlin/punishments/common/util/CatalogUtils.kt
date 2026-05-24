package punishments.common.util

import punishments.common.model.PunishmentCatalog
import punishments.common.model.PunishmentReason

/**
 * Helpers for resolving reasons and scope keys in catalogs.
 */
object CatalogUtils {

    fun findReason(catalog: PunishmentCatalog, reasonId: String?): PunishmentReason? {
        if (reasonId.isNullOrBlank()) return null
        return catalog.reasonById(reasonId)
    }

    fun isKnownScopeKey(catalog: PunishmentCatalog, key: String): Boolean {
        return catalog.capabilities.any { it.key == key }
    }
}
