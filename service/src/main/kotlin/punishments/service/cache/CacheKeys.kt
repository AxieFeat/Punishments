package punishments.service.cache

import punishments.common.dto.request.GetPunishmentsRequest
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.util.TargetKeys
import punishments.common.util.ValidationUtils
import java.security.MessageDigest

object CacheKeys {

    private const val PREFIX = "punishment-service:cache"
    private const val REV_PREFIX = "punishment-service:revision"

    const val PUBSUB_CACHE_INVALIDATE = "punishment-service:cache:invalidate"
    const val EXPIRATION_LEADER = "punishment-service:expiration:leader"

    fun punishment(id: String): String = "$PREFIX:punishment:$id"
    fun punishmentRevision(id: String): String = "$REV_PREFIX:punishment:$id"
    fun recordsRevision(): String = "$REV_PREFIX:records"

    fun activeRestrictions(
        targets: List<PunishmentTarget>,
        types: Set<PunishmentType>,
        restrictionKeys: Set<String>
    ): String {
        return "$PREFIX:active:v2:t${targetsHash(targets)}:types${types.map(PunishmentType::name).sorted().joinToString(",").sha256()}:" +
            "keys${restrictionKeys.sorted().joinToString(",").sha256()}"
    }

    fun targetRevision(targetKey: String): String = "$REV_PREFIX:target:$targetKey"

    fun list(request: GetPunishmentsRequest): String {
        return "$PREFIX:list:t${request.type?.name ?: "ALL"}:s${request.status?.name ?: "ALL"}:" +
            "o${request.sort.name}:p${request.page}:z${request.pageSize}:targets${targetsHash(request.targets)}"
    }

    fun targetList(targets: List<PunishmentTarget>, page: Int, pageSize: Int): String {
        return "$PREFIX:targets:${targetsHash(targets)}:p$page:z$pageSize"
    }

    fun search(query: String, page: Int, pageSize: Int): String {
        return "$PREFIX:search:${normalizeSearchQuery(query).sha256()}:p$page:z$pageSize"
    }

    fun catalog(version: String?): String = "$PREFIX:catalog:${version ?: "default"}"

    fun mutableReadPrefixes(): List<String> = listOf(
        "$PREFIX:list:",
        "$PREFIX:targets:",
        "$PREFIX:search:"
    )

    fun normalizeSearchQuery(query: String): String {
        return ValidationUtils.normalizeSearchQuery(query)
    }

    private fun targetsHash(targets: List<PunishmentTarget>): String {
        return targets
            .map(TargetKeys::normalized)
            .sorted()
            .joinToString("|")
            .sha256()
    }

    fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }.take(24)
    }
}
