package punishments.service.cache

import punishments.common.dto.request.GetPunishmentsRequest
import punishments.common.model.PunishmentTarget
import java.security.MessageDigest

object CacheKeys {

    private const val PREFIX = "punishments:cache"

    fun punishment(id: String): String = "$PREFIX:punishment:$id"

    fun list(request: GetPunishmentsRequest): String {
        return "$PREFIX:list:t${request.type?.name ?: "ALL"}:s${request.status?.name ?: "ALL"}:" +
            "o${request.sort.name}:p${request.page}:z${request.pageSize}:targets${targetsHash(request.targets)}"
    }

    fun targetList(targets: List<PunishmentTarget>, page: Int, pageSize: Int): String {
        return "$PREFIX:targets:${targetsHash(targets)}:p$page:z$pageSize"
    }

    fun search(query: String, page: Int, pageSize: Int): String {
        return "$PREFIX:search:${query.sha256()}:p$page:z$pageSize"
    }

    fun catalog(version: String?): String = "$PREFIX:catalog:${version ?: "default"}"

    fun namespacePattern(): String = "$PREFIX:*"

    private fun targetsHash(targets: List<PunishmentTarget>): String {
        return targets.joinToString("|") { target ->
            "${target.kind.name}:${target.id ?: ""}:${target.name.orEmpty().lowercase()}"
        }.sha256()
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }.take(24)
    }
}
