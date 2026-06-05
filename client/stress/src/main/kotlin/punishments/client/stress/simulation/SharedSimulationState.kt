package punishments.client.stress.simulation

import punishments.common.dto.response.PunishmentResponse
import punishments.common.dto.response.PunishmentSummaryResponse
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
import kotlin.time.Instant

class SharedSimulationState(
    private val maxKnownPunishments: Int = 20_000,
    private val maxKnownTargets: Int = 20_000
) {

    @Volatile
    var catalog: PunishmentCatalog? = null

    private val punishments = ConcurrentHashMap<UUID, PunishmentSummaryResponse>()
    private val punishmentOrder = ConcurrentLinkedQueue<UUID>()
    private val revocationClaims = ConcurrentHashMap.newKeySet<UUID>()
    private val targets = ConcurrentHashMap<String, PunishmentTarget>()
    private val targetOrder = ConcurrentLinkedQueue<String>()
    private val syntheticCounter = AtomicInteger(0)

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
                issuedAt = response.issuedAt,
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
        return punishments.values.asSequence().filter(predicate).shuffled().firstOrNull()
    }

    fun claimPunishmentForRevocation(nowEpochMs: Long = System.currentTimeMillis()): PunishmentSummaryResponse? {
        val candidates = punishments.values.shuffled()
        for (candidate in candidates) {
            if (candidate.status != PunishmentStatus.ACTIVE) {
                continue
            }
            if (isExpired(candidate, nowEpochMs)) {
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
        val nextStatus = if (isExpired(current, nowEpochMs)) PunishmentStatus.EXPIRED else PunishmentStatus.REVOKED
        punishments[id] = current.copy(status = nextStatus)
    }

    fun rememberTarget(target: PunishmentTarget) {
        val key = targetKey(target)
        targets[key] = target
        targetOrder.add(key)
        trimTargets()
    }

    fun randomTarget(): PunishmentTarget? {
        return targets.values.shuffled().firstOrNull()
    }

    fun randomTargets(maxCount: Int): List<PunishmentTarget> {
        if (maxCount <= 0) {
            return emptyList()
        }
        return targets.values.shuffled().take(maxCount)
    }

    fun nextSyntheticTarget(): PunishmentTarget {
        val index = syntheticCounter.incrementAndGet()
        val seed = "stress-target-$index".toByteArray(StandardCharsets.UTF_8)
        val target = PunishmentTarget(
            id = UUID.nameUUIDFromBytes(seed),
            name = "Player$index",
            kind = TargetKind.PLAYER
        )
        rememberTarget(target)
        return target
    }

    fun knownReasonIds(): List<String> {
        return catalog?.reasons?.map { it.id }.orEmpty()
    }

    fun compatibleReasonIds(type: PunishmentType): List<String> {
        val snapshot = catalog ?: return emptyList()
        return snapshot.reasons
            .filter { reason -> isReasonCompatible(reason, snapshot.capabilities, type) }
            .map(PunishmentReason::id)
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

    fun knownQueries(): List<String> {
        val queries = mutableSetOf<String>()
        knownReasonIds().forEach(queries::add)
        randomTarget()?.name?.let(queries::add)
        randomPunishment()?.id?.toString()?.let(queries::add)
        return queries.ifEmpty { listOf("spam", "abuse", "cheating") }.toList()
    }

    fun newSummary(
        id: UUID,
        type: PunishmentType,
        targets: List<PunishmentTarget>,
        reasonId: String?,
        durationSeconds: Long?
    ): PunishmentSummaryResponse {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val expiresAt = durationSeconds?.let { seconds ->
            Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + seconds * 1_000)
        }
        return PunishmentSummaryResponse(
            id = id,
            type = type,
            status = PunishmentStatus.ACTIVE,
            targets = targets,
            reasonId = reasonId,
            issuedAt = now,
            expiresAt = expiresAt
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
        return target.id?.toString() ?: "${target.kind}:${target.name.orEmpty().lowercase()}"
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
