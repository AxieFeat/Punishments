package punishments.common.protocol

/**
 * REST routes for punishment HTTP API.
 */
object Routes {

    const val API_V1 = "/api/v1"
    const val PUNISHMENTS = "$API_V1/punishments"
    const val PUNISHMENT_LIST = "$PUNISHMENTS/list"
    const val PUNISHMENT_BY_ID = "$API_V1/punishments/{id}"
    const val PUNISHMENT_REVOKE = "$API_V1/punishments/{id}/revoke"
    const val PUNISHMENT_SEARCH = "$PUNISHMENTS/search"
    const val TARGET_PUNISHMENTS = "$API_V1/targets/punishments"
    const val TARGET_RESTRICTIONS_CHECK = "$API_V1/targets/restrictions/check"
    const val ACTIVE_RESTRICTIONS = "$API_V1/targets/restrictions/active"
    const val CATALOG = "$PUNISHMENTS/catalog"

    fun punishmentById(id: Any): String = PUNISHMENT_BY_ID.replace("{id}", id.toString())

    fun punishmentRevoke(id: Any): String = PUNISHMENT_REVOKE.replace("{id}", id.toString())
}
