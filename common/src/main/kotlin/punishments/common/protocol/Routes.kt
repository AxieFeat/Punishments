package punishments.common.protocol

/**
 * REST routes for punishment HTTP API.
 */
object Routes {

    const val API_V1 = "/api/v1"
    const val PUNISHMENTS = "$API_V1/punishments"
    const val PUNISHMENT_BY_ID = "$API_V1/punishments/{id}"
    const val PUNISHMENT_REVOKE = "$API_V1/punishments/{id}/revoke"
    const val PUNISHMENT_SEARCH = "$API_V1/punishments/search"
    const val TARGET_PUNISHMENTS = "$API_V1/targets/punishments"
    const val CATALOG = "$API_V1/punishments/catalog"
}
