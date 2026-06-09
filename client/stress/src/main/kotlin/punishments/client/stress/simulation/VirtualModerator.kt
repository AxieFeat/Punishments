package punishments.client.stress.simulation

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import punishments.client.common.network.GrpcPunishmentClient
import punishments.client.stress.config.StressTestConfig
import punishments.client.stress.logging.FailureDebugLogger
import punishments.client.stress.metrics.MetricsCollector
import punishments.common.dto.request.CreatePunishmentRequest
import punishments.common.dto.request.GetCatalogRequest
import punishments.common.dto.request.GetPunishmentDetailsRequest
import punishments.common.dto.request.GetPunishmentsRequest
import punishments.common.dto.request.GetTargetPunishmentsRequest
import punishments.common.dto.request.RevokePunishmentRequest
import punishments.common.dto.request.SearchPunishmentsRequest
import punishments.common.dto.response.CreatePunishmentResult
import punishments.common.dto.response.RevokePunishmentResult
import punishments.common.error.ErrorCode
import punishments.common.model.ActorSource
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentSort
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class VirtualModerator(
    private val identity: VirtualModeratorIdentity,
    private val behavior: BehaviorType,
    private val api: GrpcPunishmentClient,
    private val sharedState: SharedSimulationState,
    private val metrics: MetricsCollector,
    private val config: StressTestConfig
) {
    private val actor = PunishmentActor(id = identity.uuid, name = identity.name, source = ActorSource.STAFF)

    suspend fun runUntil(deadlineMs: Long) {
        while (coroutineContext.isActive && System.currentTimeMillis() < deadlineMs) {
            val action = selectAction()
            try {
                when (action) {
                    Action.GET_CATALOG -> getCatalog()
                    Action.LIST_PUNISHMENTS -> listPunishments()
                    Action.GET_TARGET_PUNISHMENTS -> getTargetPunishments()
                    Action.SEARCH_PUNISHMENTS -> searchPunishments()
                    Action.VIEW_PUNISHMENT -> viewPunishment()
                    Action.CREATE_PUNISHMENT -> createPunishment()
                    Action.REVOKE_PUNISHMENT -> revokePunishment()
                }
            } catch (e: Exception) {
                metrics.record(action.operationName, false, 0)
                FailureDebugLogger.logException(
                    operation = action.operationName,
                    throwable = e,
                    message = "[${identity.name}] action ${action.name} failed: ${e.message}"
                )
            }

            delay(getActionDelay())
        }
    }

    private suspend fun getCatalog() {
        val (response, latency) = timed { api.getCatalog(GetCatalogRequest()) }
        sharedState.catalog = response.catalog
        metrics.record("get_catalog", true, latency)
    }

    private suspend fun listPunishments() {
        val request = GetPunishmentsRequest(
            type = randomTypeOrNull(),
            status = randomStatusOrNull(),
            sort = PunishmentSort.entries.random(),
            page = pickRealisticPage(),
            pageSize = config.pageSize
        )
        val (response, latency) = timed { api.getPunishments(request) }
        sharedState.rememberPunishments(response.items)
        metrics.record("list_punishments", true, latency)
    }

    private suspend fun getTargetPunishments() {
        val targets = sharedState.randomTargets(Random.nextInt(1, 4)).ifEmpty {
            listOf(sharedState.nextSyntheticTarget())
        }
        val request = GetTargetPunishmentsRequest(
            targets = targets,
            page = pickRealisticPage(),
            pageSize = config.pageSize
        )
        val (response, latency) = timed { api.getTargetPunishments(request) }
        sharedState.rememberPunishments(response.items)
        metrics.record("get_target_punishments", true, latency)
    }

    private suspend fun searchPunishments() {
        val query = sharedState.knownQueries().random()
        val request = SearchPunishmentsRequest(
            query = query,
            page = pickRealisticPage(),
            pageSize = config.pageSize
        )
        val (response, latency) = timed { api.searchPunishments(request) }
        sharedState.rememberPunishments(response.items)
        metrics.record("search_punishments", true, latency)
    }

    private suspend fun viewPunishment() {
        val summary = sharedState.randomPunishment() ?: return
        val (response, latency) = timed { api.getPunishmentDetails(GetPunishmentDetailsRequest(summary.id)) }
        metrics.record("view_punishment", response != null, latency)
        if (response == null) {
            sharedState.removePunishment(summary.id)
            FailureDebugLogger.logFailure(
                operation = "view_punishment",
                signature = "not_found",
                message = "[${identity.name}] getPunishment returned null"
            )
            return
        }
        sharedState.rememberPunishment(response)
    }

    private suspend fun createPunishment() {
        val target = if (Random.nextDouble() < 0.8) {
            sharedState.nextSyntheticTarget()
        } else {
            sharedState.randomTarget() ?: sharedState.nextSyntheticTarget()
        }
        val type = when (behavior) {
            BehaviorType.OBSERVER -> PunishmentType.WARN
            BehaviorType.MODERATOR -> listOf(
                PunishmentType.MUTE,
                PunishmentType.BAN,
                PunishmentType.WARN,
                PunishmentType.KICK
            ).random()
            BehaviorType.AUDITOR -> PunishmentType.WARN
            BehaviorType.CHAOTIC -> PunishmentType.entries.random()
            BehaviorType.AFK -> PunishmentType.WARN
        }
        val durationSeconds = when (type) {
            PunishmentType.WARN -> null
            PunishmentType.MUTE -> listOf(300L, 900L, 3_600L, 21_600L).random()
            PunishmentType.BAN -> listOf(3_600L, 86_400L, 604_800L).random()
            PunishmentType.KICK -> null
        }
        val reasonId = sharedState.compatibleReasonIds(type).randomOrNull()
        val scope = resolveScope(type, reasonId)
        val request = CreatePunishmentRequest(
            type = type,
            selection = TargetSelection(selector = target.name, targets = listOf(target)),
            scope = scope,
            reasonId = reasonId,
            reasonText = if (reasonId == null) "stress test" else null,
            durationSeconds = durationSeconds,
            issuer = actor
        )

        val (result, latency) = timed { api.createPunishment(request) }
        val success = result is CreatePunishmentResult.Success
        metrics.record("create_punishment", success, latency)

        when (result) {
            is CreatePunishmentResult.Success -> {
                metrics.incrementCounter("created_punishments", result.createdIds.size.toLong())
                result.createdIds.forEach { id ->
                    sharedState.rememberPunishment(
                        sharedState.newSummary(id, type, listOf(target), reasonId, durationSeconds, actor)
                    )
                }
            }
            is CreatePunishmentResult.Error -> {
                FailureDebugLogger.logFailure(
                    operation = "create_punishment",
                    signature = "${result.code}:${result.message}",
                    message = "[${identity.name}] create failed: ${result.code} - ${result.message}"
                )
            }
        }
    }

    private suspend fun revokePunishment() {
        val summary = sharedState.claimPunishmentForRevocation() ?: return
        try {
            val request = RevokePunishmentRequest(
                punishmentId = summary.id,
                reason = "stress revoke",
                actor = actor
            )
            val (result, latency) = timed { api.revokePunishment(request) }
            val success = result is RevokePunishmentResult.Success
            metrics.record("revoke_punishment", success, latency)

            when (result) {
                is RevokePunishmentResult.Success -> sharedState.updatePunishmentStatus(summary.id, PunishmentStatus.REVOKED)
                is RevokePunishmentResult.Error -> {
                    handleRevokeFailure(summary.id, result)
                    FailureDebugLogger.logFailure(
                        operation = "revoke_punishment",
                        signature = "${result.code}:${result.message}",
                        message = "[${identity.name}] revoke failed: ${result.code} - ${result.message}"
                    )
                }
            }
        } finally {
            sharedState.releaseRevocationClaim(summary.id)
        }
    }

    private fun selectAction(): Action {
        val weights = when (behavior) {
            BehaviorType.OBSERVER -> mapOf(
                Action.LIST_PUNISHMENTS to 25,
                Action.GET_TARGET_PUNISHMENTS to 20,
                Action.SEARCH_PUNISHMENTS to 20,
                Action.VIEW_PUNISHMENT to 20,
                Action.GET_CATALOG to 10,
                Action.CREATE_PUNISHMENT to 3,
                Action.REVOKE_PUNISHMENT to 2
            )
            BehaviorType.MODERATOR -> mapOf(
                Action.CREATE_PUNISHMENT to 28,
                Action.REVOKE_PUNISHMENT to 18,
                Action.LIST_PUNISHMENTS to 16,
                Action.SEARCH_PUNISHMENTS to 12,
                Action.VIEW_PUNISHMENT to 12,
                Action.GET_TARGET_PUNISHMENTS to 8,
                Action.GET_CATALOG to 6
            )
            BehaviorType.AUDITOR -> mapOf(
                Action.LIST_PUNISHMENTS to 20,
                Action.GET_TARGET_PUNISHMENTS to 20,
                Action.SEARCH_PUNISHMENTS to 20,
                Action.VIEW_PUNISHMENT to 15,
                Action.GET_CATALOG to 15,
                Action.REVOKE_PUNISHMENT to 7,
                Action.CREATE_PUNISHMENT to 3
            )
            BehaviorType.CHAOTIC -> mapOf(
                Action.CREATE_PUNISHMENT to 22,
                Action.REVOKE_PUNISHMENT to 18,
                Action.LIST_PUNISHMENTS to 14,
                Action.GET_TARGET_PUNISHMENTS to 12,
                Action.SEARCH_PUNISHMENTS to 12,
                Action.VIEW_PUNISHMENT to 12,
                Action.GET_CATALOG to 10
            )
            BehaviorType.AFK -> mapOf(
                Action.GET_CATALOG to 30,
                Action.LIST_PUNISHMENTS to 25,
                Action.VIEW_PUNISHMENT to 20,
                Action.SEARCH_PUNISHMENTS to 15,
                Action.GET_TARGET_PUNISHMENTS to 10
            )
        }
        return weightedRandom(weights)
    }

    private fun randomTypeOrNull(): PunishmentType? {
        return if (Random.nextDouble() < 0.35) PunishmentType.entries.random() else null
    }

    private fun randomStatusOrNull(): PunishmentStatus? {
        return if (Random.nextDouble() < 0.45) PunishmentStatus.entries.random() else null
    }

    private fun pickRealisticPage(): Int {
        if (config.maxBrowsePage <= 0) {
            return 0
        }

        val lambda = 0.55
        val raw = -kotlin.math.ln(1.0 - Random.nextDouble()) / lambda
        return raw.toInt().coerceIn(0, config.maxBrowsePage)
    }

    private fun getActionDelay(): Long {
        return when (behavior) {
            BehaviorType.OBSERVER -> Random.nextLong(1_500L, 5_000L)
            BehaviorType.MODERATOR -> Random.nextLong(500L, 2_500L)
            BehaviorType.AUDITOR -> Random.nextLong(1_000L, 4_000L)
            BehaviorType.CHAOTIC -> Random.nextLong(100L, 1_200L)
            BehaviorType.AFK -> Random.nextLong(5_000L, 20_000L)
        }
    }

    private fun <T> weightedRandom(weights: Map<T, Int>): T {
        val totalWeight = weights.values.sum()
        var value = Random.nextInt(totalWeight)
        for ((item, weight) in weights) {
            value -= weight
            if (value < 0) {
                return item
            }
        }
        return weights.keys.first()
    }

    private suspend fun <T> timed(block: suspend () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val result = block()
        val elapsed = (System.nanoTime() - start) / 1_000_000
        return result to elapsed
    }

    private fun resolveScope(type: PunishmentType, reasonId: String?): PunishmentScope {
        if (type == PunishmentType.KICK) {
            return PunishmentScope()
        }

        if (reasonId != null) {
            return PunishmentScope(sharedState.recommendedScopeKeys(reasonId))
        }

        val allowedKeys = sharedState.compatibleScopeKeys(type).toList()
        if (allowedKeys.isEmpty()) {
            return PunishmentScope()
        }

        val selectionSize = Random.nextInt(1, allowedKeys.size + 1)
        return PunishmentScope(allowedKeys.shuffled().take(selectionSize).toSet())
    }

    private fun handleRevokeFailure(
        punishmentId: java.util.UUID,
        result: RevokePunishmentResult.Error
    ) {
        when (result.code) {
            ErrorCode.PUNISHMENT_ALREADY_REVOKED -> sharedState.updatePunishmentStatus(
                punishmentId,
                PunishmentStatus.REVOKED
            )
            ErrorCode.PUNISHMENT_NOT_FOUND -> sharedState.removePunishment(punishmentId)
            ErrorCode.INVALID_REQUEST -> {
                if (result.message.contains("no longer active", ignoreCase = true)) {
                    sharedState.markPunishmentNonActive(punishmentId)
                }
            }
            else -> Unit
        }
    }

    private enum class Action(val operationName: String) {
        GET_CATALOG("get_catalog"),
        LIST_PUNISHMENTS("list_punishments"),
        GET_TARGET_PUNISHMENTS("get_target_punishments"),
        SEARCH_PUNISHMENTS("search_punishments"),
        VIEW_PUNISHMENT("view_punishment"),
        CREATE_PUNISHMENT("create_punishment"),
        REVOKE_PUNISHMENT("revoke_punishment")
    }
}
