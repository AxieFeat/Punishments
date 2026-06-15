package punishments.client.stress.simulation

import punishments.common.dto.response.PunishmentResponse
import punishments.common.dto.response.PunishmentSummaryResponse
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentCapability
import punishments.common.model.PunishmentCatalog
import punishments.common.model.PunishmentReason
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetKind
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Instant

class SharedSimulationState(
    private val maxKnownPunishments: Int = 25_000,
    private val maxKnownTargets: Int = 25_000
) {
    private val popularSearchQueryShare = 0.85

    @Volatile
    var catalog: PunishmentCatalog? = null

    private val punishments = ConcurrentHashMap<UUID, PunishmentSummaryResponse>()
    private val punishmentOrder = ConcurrentLinkedQueue<UUID>()
    private val revocationClaims = ConcurrentHashMap.newKeySet<UUID>()
    private val targets = ConcurrentHashMap<String, PunishmentTarget>()
    private val targetOrder = ConcurrentLinkedQueue<String>()
    private val hotTargetsByServer = ConcurrentHashMap<String, List<PunishmentTarget>>()
    private val syntheticCounter = AtomicInteger(0)

    fun seedHotTargets(serverIds: List<String>, hotTargetPoolSize: Int) {
        serverIds.forEach { serverId ->
            val hotTargets = List(hotTargetPoolSize) { index ->
                buildTarget(serverId = serverId, label = "Hot${index + 1}", hot = true)
            }
            hotTargets.forEach(::rememberTarget)
            hotTargetsByServer[serverId] = hotTargets
        }
    }

    fun rememberPunishments(items: List<PunishmentSummaryResponse>) {
        items.forEach(::rememberPunishment)
    }

    fun rememberPunishment(response: PunishmentResponse) {
        rememberPunishment(
            PunishmentSummaryResponse(
                id = response.id,
                type = response.type,
                status = response.status,
                targets = response.targets,
                reasonId = response.reasonId,
                reasonText = response.reasonText,
                issuedAt = response.issuedAt,
                issuedBy = response.issuedBy,
                expiresAt = response.expiresAt
            )
        )
    }

    fun rememberPunishment(summary: PunishmentSummaryResponse) {
        punishments[summary.id] = summary
        punishmentOrder.add(summary.id)
        summary.targets.forEach(::rememberTarget)
        trimPunishments()
    }

    fun updatePunishmentStatus(id: UUID, status: PunishmentStatus) {
        val current = punishments[id] ?: return
        punishments[id] = current.copy(status = status)
    }

    fun removePunishment(id: UUID) {
        punishments.remove(id)
    }

    fun randomPunishment(predicate: (PunishmentSummaryResponse) -> Boolean = { true }): PunishmentSummaryResponse? {
        return punishments.values.toList().shuffled().firstOrNull(predicate)
    }

    fun claimPunishmentForRevocation(nowEpochMs: Long = System.currentTimeMillis()): PunishmentSummaryResponse? {
        for (candidate in punishments.values.toList().shuffled()) {
            if (candidate.status != PunishmentStatus.ACTIVE || isExpired(candidate, nowEpochMs)) {
                continue
            }
            if (revocationClaims.add(candidate.id)) {
                return candidate
            }
        }
        return null
    }

    fun releaseRevocationClaim(id: UUID) {
        revocationClaims.remove(id)
    }

    fun markPunishmentNonActive(id: UUID, nowEpochMs: Long = System.currentTimeMillis()) {
        val current = punishments[id] ?: return
        punishments[id] = current.copy(
            status = if (isExpired(current, nowEpochMs)) PunishmentStatus.EXPIRED else PunishmentStatus.REVOKED
        )
    }

    fun rememberTarget(target: PunishmentTarget) {
        val key = targetKey(target)
        targets[key] = target
        targetOrder.add(key)
        trimTargets()
    }

    fun randomTarget(): PunishmentTarget? {
        return targets.values.toList().randomOrNull()
    }

    fun randomTargets(maxCount: Int): List<PunishmentTarget> {
        return targets.values.toList().shuffled().take(maxCount.coerceAtLeast(0))
    }

    fun pickCreateTarget(serverId: String, hotTargetShare: Double): PunishmentTarget {
        val chooseHotTarget = Random.nextDouble() < hotTargetShare
        return when {
            chooseHotTarget -> hotTarget(serverId)
            else -> randomTarget() ?: nextSyntheticTarget(serverId)
        }
    }

    fun hotTarget(serverId: String): PunishmentTarget {
        return hotTargetsByServer[serverId]?.randomOrNull() ?: nextSyntheticTarget(serverId, hot = true)
    }

    fun nextSyntheticTarget(serverId: String, hot: Boolean = false): PunishmentTarget {
        val index = syntheticCounter.incrementAndGet()
        val label = if (hot) "Hot$index" else "Player$index"
        val target = buildTarget(serverId = serverId, label = label, hot = hot)
        rememberTarget(target)
        return target
    }

    fun compatibleReasonIds(type: PunishmentType): List<String> {
        val snapshot = catalog ?: return emptyList()
        if (type == PunishmentType.KICK) {
            return snapshot.reasons.map(PunishmentReason::id)
        }
        return snapshot.reasons
            .filter { reason -> isReasonCompatible(reason, snapshot.capabilities, type) }
            .map(PunishmentReason::id)
    }

    fun recommendedDurationSeconds(reasonId: String?): Long? {
        return reasonId?.let { id -> catalog?.reasonById(id)?.recommendedDurationSeconds }
    }

    fun compatibleScopeKeys(type: PunishmentType): Set<String> {
        return catalog
            ?.capabilities
            ?.filter { capability -> capability.appliesTo.isEmpty() || type in capability.appliesTo }
            ?.map(PunishmentCapability::key)
            ?.toSet()
            .orEmpty()
    }

    fun recommendedScopeKeys(reasonId: String): Set<String> {
        return catalog?.reasonById(reasonId)?.recommendedScopeKeys.orEmpty()
    }

    fun pickSearchQuery(): String {
        val popular = popularSearchQueries()
        if (Random.nextDouble() < popularSearchQueryShare) {
            return popular.random()
        }

        val dynamic = dynamicSearchQueries()
        return dynamic.ifEmpty { popular }.random()
    }

    fun knownQueries(): List<String> {
        return (popularSearchQueries() + dynamicSearchQueries()).distinct()
    }

    private fun popularSearchQueries(): List<String> {
        val queries = linkedSetOf<String>()
        catalog?.reasons?.take(10)?.mapTo(queries, PunishmentReason::id)
        queries += listOf("spam", "abuse", "scam", "mute", "ban")
        return queries.toList()
    }

    private fun dynamicSearchQueries(): List<String> {
        val queries = linkedSetOf<String>()
        randomTarget()?.name?.let(queries::add)
        randomPunishment()?.id?.toString()?.let(queries::add)
        return queries.toList()
    }

    fun newSummary(
        id: UUID,
        type: PunishmentType,
        targets: List<PunishmentTarget>,
        reasonId: String?,
        reasonText: String?,
        durationSeconds: Long?,
        issuedBy: PunishmentActor
    ): PunishmentSummaryResponse {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val expiresAt = if (type == PunishmentType.KICK) {
            null
        } else {
            durationSeconds?.let { seconds ->
                Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + seconds * 1_000)
            }
        }
        return PunishmentSummaryResponse(
            id = id,
            type = type,
            status = if (type == PunishmentType.KICK) PunishmentStatus.EXPIRED else PunishmentStatus.ACTIVE,
            targets = targets,
            reasonId = reasonId,
            reasonText = reasonText,
            issuedAt = now,
            issuedBy = issuedBy,
            expiresAt = expiresAt
        )
    }

    private fun buildTarget(serverId: String, label: String, hot: Boolean): PunishmentTarget {
        val seed = "$serverId:$label".toByteArray(StandardCharsets.UTF_8)
        return PunishmentTarget(
            id = UUID.nameUUIDFromBytes(seed),
            name = if (hot) "$serverId-$label" else "$serverId-$label",
            targetType = TargetKind.PLAYER
        )
    }

    private fun trimPunishments() {
        while (punishments.size > maxKnownPunishments) {
            val candidate = punishmentOrder.poll() ?: break
            punishments.remove(candidate)
        }
    }

    private fun trimTargets() {
        while (targets.size > maxKnownTargets) {
            val candidate = targetOrder.poll() ?: break
            targets.remove(candidate)
        }
    }

    private fun targetKey(target: PunishmentTarget): String {
        return target.id?.toString() ?: "${target.targetType}:${target.name.orEmpty().lowercase()}"
    }

    private fun isReasonCompatible(
        reason: PunishmentReason,
        capabilities: List<PunishmentCapability>,
        type: PunishmentType
    ): Boolean {
        return reason.recommendedScopeKeys.all { key ->
            val capability = capabilities.firstOrNull { it.key == key } ?: return false
            capability.appliesTo.isEmpty() || type in capability.appliesTo
        }
    }

    private fun isExpired(summary: PunishmentSummaryResponse, nowEpochMs: Long): Boolean {
        return summary.expiresAt?.toEpochMilliseconds()?.let { expiresAt -> expiresAt <= nowEpochMs } == true
    }
}
