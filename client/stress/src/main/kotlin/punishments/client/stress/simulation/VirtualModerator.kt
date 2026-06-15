package punishments.client.stress.simulation

import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import punishments.client.stress.config.StressTestConfig
import punishments.client.stress.config.WorkloadProfile
import punishments.client.stress.logging.FailureDebugLogger
import punishments.client.stress.metrics.MetricsCollector
import punishments.client.stress.metrics.OperationEvent
import punishments.common.dto.request.CheckTargetRestrictionsRequest
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
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class VirtualModerator(
    private val runId: String,
    private val identity: VirtualModeratorIdentity,
    private val api: StressApiAdapter,
    private val sharedState: SharedSimulationState,
    private val metrics: MetricsCollector,
    private val config: StressTestConfig,
    private val pageDistribution: ExponentialPageDistribution
) {
    private val actor = PunishmentActor(
        id = identity.uuid,
        name = identity.name,
        source = ActorSource.STAFF
    )

    suspend fun run(loadState: StateFlow<RuntimeLoadState>) {
        while (coroutineContext.isActive && !loadState.value.completed) {
            val state = loadState.value
            if (!state.isActive(identity.workerIndex)) {
                delay(IDLE_POLL_MS)
                continue
            }

            when (selectOperation()) {
                Operation.GET_CATALOG -> getCatalog(state)
                Operation.LIST_PUNISHMENTS -> listPunishments(state)
                Operation.GET_TARGET_PUNISHMENTS -> getTargetPunishments(state)
                Operation.SEARCH_PUNISHMENTS -> searchPunishments(state)
                Operation.VIEW_PUNISHMENT -> viewPunishment(state)
                Operation.CREATE_PUNISHMENT -> createPunishment(state, hotTarget = false)
                Operation.REVOKE_PUNISHMENT -> revokePunishment(state)
                Operation.ENFORCE_HOT_TARGET -> enforceHotTarget(state)
            }

            delay(thinkTimeMs())
        }
    }

    private suspend fun getCatalog(state: RuntimeLoadState) {
        when (val execution = api.execute { getCatalog(GetCatalogRequest()) }) {
            is ApiExecution.Success -> {
                sharedState.catalog = execution.value.catalog
                record(state, Operation.GET_CATALOG, true, execution.latencyMs, execution.retries)
            }

            is ApiExecution.Failure -> recordFailure(state, Operation.GET_CATALOG, execution)
        }
    }

    private suspend fun listPunishments(state: RuntimeLoadState) {
        val request = GetPunishmentsRequest(
            type = randomTypeOrNull(),
            status = randomStatusOrNull(),
            sort = PunishmentSort.entries.random(),
            page = pageDistribution.pick(),
            pageSize = config.pageSize
        )
        when (val execution = api.execute { getPunishments(request) }) {
            is ApiExecution.Success -> {
                sharedState.rememberPunishments(execution.value.items)
                record(state, Operation.LIST_PUNISHMENTS, true, execution.latencyMs, execution.retries)
            }

            is ApiExecution.Failure -> recordFailure(state, Operation.LIST_PUNISHMENTS, execution)
        }
    }

    private suspend fun getTargetPunishments(state: RuntimeLoadState) {
        val targets = sharedState.randomTargets(Random.nextInt(1, 4)).ifEmpty {
            listOf(sharedState.nextSyntheticTarget(identity.serverId))
        }
        val request = GetTargetPunishmentsRequest(
            targets = targets,
            page = pageDistribution.pick(),
            pageSize = config.pageSize
        )
        when (val execution = api.execute { getTargetPunishments(request) }) {
            is ApiExecution.Success -> {
                sharedState.rememberPunishments(execution.value.items)
                record(state, Operation.GET_TARGET_PUNISHMENTS, true, execution.latencyMs, execution.retries)
            }

            is ApiExecution.Failure -> recordFailure(state, Operation.GET_TARGET_PUNISHMENTS, execution)
        }
    }

    private suspend fun searchPunishments(state: RuntimeLoadState) {
        val request = SearchPunishmentsRequest(
            query = sharedState.pickSearchQuery(),
            page = pageDistribution.pick(),
            pageSize = config.pageSize
        )
        when (val execution = api.execute { searchPunishments(request) }) {
            is ApiExecution.Success -> {
                sharedState.rememberPunishments(execution.value.items)
                record(state, Operation.SEARCH_PUNISHMENTS, true, execution.latencyMs, execution.retries)
            }

            is ApiExecution.Failure -> recordFailure(state, Operation.SEARCH_PUNISHMENTS, execution)
        }
    }

    private suspend fun viewPunishment(state: RuntimeLoadState) {
        val summary = sharedState.randomPunishment()
        if (summary == null) {
            listPunishments(state)
            return
        }
        when (val execution = api.execute { getPunishmentDetails(GetPunishmentDetailsRequest(summary.id)) }) {
            is ApiExecution.Success -> {
                val response = execution.value
                if (response == null) {
                    sharedState.removePunishment(summary.id)
                    record(state, Operation.VIEW_PUNISHMENT, false, execution.latencyMs, execution.retries, "NOT_FOUND")
                    FailureDebugLogger.logFailure(
                        operation = Operation.VIEW_PUNISHMENT.metricName,
                        signature = "null-response",
                        message = "[${identity.name}] getPunishmentDetails returned null"
                    )
                } else {
                    sharedState.rememberPunishment(response)
                    record(state, Operation.VIEW_PUNISHMENT, true, execution.latencyMs, execution.retries)
                }
            }

            is ApiExecution.Failure -> recordFailure(state, Operation.VIEW_PUNISHMENT, execution)
        }
    }

    private suspend fun createPunishment(state: RuntimeLoadState, hotTarget: Boolean) {
        val target = if (hotTarget) {
            sharedState.hotTarget(identity.serverId)
        } else {
            sharedState.pickCreateTarget(identity.serverId, config.hotTargetShare)
        }
        val type = selectPunishmentType(hotTarget)
        val reasonId = sharedState.compatibleReasonIds(type).randomOrNull()
        val reasonText = reasonId ?: "stress-${config.profile.cliName}"
        val durationSeconds = resolveDuration(type, reasonId)
        val request = CreatePunishmentRequest(
            type = type,
            selection = TargetSelection(selector = target.name, targets = listOf(target)),
            scope = resolveScope(type, reasonId),
            reasonId = reasonId,
            reasonText = if (reasonId == null) reasonText else null,
            durationSeconds = durationSeconds,
            issuer = actor
        )

        val operation = if (hotTarget) Operation.ENFORCE_HOT_TARGET else Operation.CREATE_PUNISHMENT
        when (val execution = api.execute { createPunishment(request) }) {
            is ApiExecution.Success -> {
                when (val result = execution.value) {
                    is CreatePunishmentResult.Success -> {
                        result.createdIds.forEach { id ->
                            sharedState.rememberPunishment(
                                sharedState.newSummary(
                                    id = id,
                                    type = type,
                                    targets = listOf(target),
                                    reasonId = reasonId,
                                    reasonText = if (reasonId == null) reasonText else null,
                                    durationSeconds = durationSeconds,
                                    issuedBy = actor
                                )
                            )
                        }
                        record(state, operation, true, execution.latencyMs, execution.retries)
                    }

                    is CreatePunishmentResult.Error -> {
                        record(
                            state = state,
                            operation = operation,
                            success = false,
                            latencyMs = execution.latencyMs,
                            retries = execution.retries,
                            errorCode = result.code.name
                        )
                        FailureDebugLogger.logFailure(
                            operation = operation.metricName,
                            signature = "${result.code}:${result.message}",
                            message = "[${identity.name}] create failed: ${result.code} - ${result.message}"
                        )
                    }
                }
            }

            is ApiExecution.Failure -> recordFailure(state, operation, execution)
        }
    }

    private suspend fun revokePunishment(state: RuntimeLoadState) {
        val summary = sharedState.claimPunishmentForRevocation()
        if (summary == null) {
            listPunishments(state)
            return
        }

        try {
            val request = RevokePunishmentRequest(
                punishmentId = summary.id,
                reason = "stress revoke",
                actor = actor
            )
            when (val execution = api.execute { revokePunishment(request) }) {
                is ApiExecution.Success -> {
                    when (val result = execution.value) {
                        is RevokePunishmentResult.Success -> {
                            sharedState.updatePunishmentStatus(summary.id, PunishmentStatus.REVOKED)
                            record(state, Operation.REVOKE_PUNISHMENT, true, execution.latencyMs, execution.retries)
                        }

                        is RevokePunishmentResult.Error -> {
                            handleRevokeFailure(summary.id, result)
                            record(
                                state = state,
                                operation = Operation.REVOKE_PUNISHMENT,
                                success = false,
                                latencyMs = execution.latencyMs,
                                retries = execution.retries,
                                errorCode = result.code.name
                            )
                            FailureDebugLogger.logFailure(
                                operation = Operation.REVOKE_PUNISHMENT.metricName,
                                signature = "${result.code}:${result.message}",
                                message = "[${identity.name}] revoke failed: ${result.code} - ${result.message}"
                            )
                        }
                    }
                }

                is ApiExecution.Failure -> recordFailure(state, Operation.REVOKE_PUNISHMENT, execution)
            }
        } finally {
            sharedState.releaseRevocationClaim(summary.id)
        }
    }

    private suspend fun enforceHotTarget(state: RuntimeLoadState) {
        val target = sharedState.hotTarget(identity.serverId)
        val request = CheckTargetRestrictionsRequest(
            targets = listOf(target),
            types = setOf(PunishmentType.BAN, PunishmentType.MUTE)
        )
        when (val execution = api.execute { checkTargetRestrictions(request) }) {
            is ApiExecution.Success -> record(
                state = state,
                operation = Operation.ENFORCE_HOT_TARGET,
                success = true,
                latencyMs = execution.latencyMs,
                retries = execution.retries,
                errorCode = if (execution.value.restricted) "RESTRICTED" else null
            )

            is ApiExecution.Failure -> recordFailure(state, Operation.ENFORCE_HOT_TARGET, execution)
        }
    }


    private fun selectOperation(): Operation {
        val weights = when (config.profile) {
            WorkloadProfile.OBSERVER -> mapOf(
                Operation.LIST_PUNISHMENTS to 28,
                Operation.GET_TARGET_PUNISHMENTS to 22,
                Operation.SEARCH_PUNISHMENTS to 20,
                Operation.VIEW_PUNISHMENT to 18,
                Operation.GET_CATALOG to 10,
                Operation.CREATE_PUNISHMENT to 1,
                Operation.REVOKE_PUNISHMENT to 1
            )

            WorkloadProfile.MODERATION -> mapOf(
                Operation.CREATE_PUNISHMENT to 26,
                Operation.REVOKE_PUNISHMENT to 18,
                Operation.LIST_PUNISHMENTS to 16,
                Operation.SEARCH_PUNISHMENTS to 12,
                Operation.VIEW_PUNISHMENT to 12,
                Operation.GET_TARGET_PUNISHMENTS to 8,
                Operation.GET_CATALOG to 8
            )

            WorkloadProfile.AUDIT -> mapOf(
                Operation.LIST_PUNISHMENTS to 24,
                Operation.GET_TARGET_PUNISHMENTS to 20,
                Operation.SEARCH_PUNISHMENTS to 18,
                Operation.VIEW_PUNISHMENT to 16,
                Operation.GET_CATALOG to 12,
                Operation.REVOKE_PUNISHMENT to 6,
                Operation.CREATE_PUNISHMENT to 4
            )

            WorkloadProfile.REVOKE_HEAVY -> mapOf(
                Operation.REVOKE_PUNISHMENT to 30,
                Operation.LIST_PUNISHMENTS to 18,
                Operation.GET_TARGET_PUNISHMENTS to 14,
                Operation.VIEW_PUNISHMENT to 12,
                Operation.SEARCH_PUNISHMENTS to 10,
                Operation.CREATE_PUNISHMENT to 10,
                Operation.GET_CATALOG to 6
            )

            WorkloadProfile.CHAOS -> mapOf(
                Operation.CREATE_PUNISHMENT to 20,
                Operation.ENFORCE_HOT_TARGET to 16,
                Operation.REVOKE_PUNISHMENT to 14,
                Operation.LIST_PUNISHMENTS to 12,
                Operation.GET_TARGET_PUNISHMENTS to 12,
                Operation.SEARCH_PUNISHMENTS to 10,
                Operation.VIEW_PUNISHMENT to 10,
                Operation.GET_CATALOG to 6
            )

            WorkloadProfile.ENFORCEMENT_HEAVY -> mapOf(
                Operation.ENFORCE_HOT_TARGET to 34,
                Operation.CREATE_PUNISHMENT to 20,
                Operation.REVOKE_PUNISHMENT to 14,
                Operation.LIST_PUNISHMENTS to 10,
                Operation.GET_TARGET_PUNISHMENTS to 8,
                Operation.SEARCH_PUNISHMENTS to 6,
                Operation.VIEW_PUNISHMENT to 4,
                Operation.GET_CATALOG to 4
            )
        }
        return weightedRandom(weights)
    }

    private fun thinkTimeMs(): Long {
        return when (config.profile) {
            WorkloadProfile.OBSERVER -> Random.nextLong(1_000L, 3_500L)
            WorkloadProfile.MODERATION -> Random.nextLong(350L, 1_500L)
            WorkloadProfile.AUDIT -> Random.nextLong(800L, 2_200L)
            WorkloadProfile.REVOKE_HEAVY -> Random.nextLong(250L, 1_100L)
            WorkloadProfile.CHAOS -> Random.nextLong(80L, 600L)
            WorkloadProfile.ENFORCEMENT_HEAVY -> Random.nextLong(120L, 800L)
        }
    }

    private fun selectPunishmentType(hotTarget: Boolean): PunishmentType {
        val hotWeights = listOf(PunishmentType.MUTE, PunishmentType.BAN, PunishmentType.WARN, PunishmentType.KICK)
        return when (config.profile) {
            WorkloadProfile.OBSERVER -> PunishmentType.WARN
            WorkloadProfile.MODERATION -> hotWeights.random()
            WorkloadProfile.AUDIT -> PunishmentType.WARN
            WorkloadProfile.REVOKE_HEAVY -> listOf(PunishmentType.MUTE, PunishmentType.BAN, PunishmentType.WARN).random()
            WorkloadProfile.CHAOS -> PunishmentType.entries.random()
            WorkloadProfile.ENFORCEMENT_HEAVY -> if (hotTarget) hotWeights.random() else listOf(
                PunishmentType.MUTE,
                PunishmentType.BAN
            ).random()
        }
    }

    private fun resolveDuration(type: PunishmentType, reasonId: String?): Long? {
        if (type == PunishmentType.WARN || type == PunishmentType.KICK) {
            return null
        }
        return sharedState.recommendedDurationSeconds(reasonId) ?: when (type) {
            PunishmentType.MUTE -> listOf(300L, 900L, 3_600L, 21_600L).random()
            PunishmentType.BAN -> listOf(3_600L, 86_400L, 604_800L).random()
            PunishmentType.WARN, PunishmentType.KICK -> null
        }
    }

    private fun resolveScope(type: PunishmentType, reasonId: String?): PunishmentScope {
        if (type == PunishmentType.KICK) {
            return PunishmentScope()
        }
        val recommended = reasonId?.let(sharedState::recommendedScopeKeys).orEmpty()
        if (recommended.isNotEmpty()) {
            return PunishmentScope(recommended)
        }
        val available = sharedState.compatibleScopeKeys(type).toList()
        if (available.isEmpty()) {
            return PunishmentScope()
        }
        val selectionSize = Random.nextInt(1, available.size + 1)
        return PunishmentScope(available.shuffled().take(selectionSize).toSet())
    }

    private fun randomTypeOrNull(): PunishmentType? {
        return if (Random.nextDouble() < 0.35) PunishmentType.entries.random() else null
    }

    private fun randomStatusOrNull(): PunishmentStatus? {
        return if (Random.nextDouble() < 0.45) PunishmentStatus.entries.random() else null
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

    private fun weightedRandom(weights: Map<Operation, Int>): Operation {
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

    private fun record(
        state: RuntimeLoadState,
        operation: Operation,
        success: Boolean,
        latencyMs: Long,
        retries: Int,
        errorCode: String? = null
    ) {
        metrics.record(
            OperationEvent(
                runId = runId,
                timestamp = Instant.now().toString(),
                phase = state.phase,
                serverId = identity.serverId,
                clientName = identity.name,
                profile = config.profile.cliName,
                operation = operation.metricName,
                success = success,
                latencyMs = latencyMs,
                retries = retries,
                errorCode = errorCode
            )
        )
    }

    private fun recordFailure(state: RuntimeLoadState, operation: Operation, failure: ApiExecution.Failure) {
        record(
            state = state,
            operation = operation,
            success = false,
            latencyMs = failure.latencyMs,
            retries = failure.retries,
            errorCode = failure.errorCode
        )
        FailureDebugLogger.logException(
            operation = operation.metricName,
            throwable = failure.throwable,
            message = "[${identity.name}] ${operation.metricName} failed: code=${failure.errorCode}, " +
                "latency=${failure.latencyMs}ms, retries=${failure.retries}, message=${failure.throwable.message}"
        )
    }

    private enum class Operation(val metricName: String) {
        GET_CATALOG("get_catalog"),
        LIST_PUNISHMENTS("list_punishments"),
        GET_TARGET_PUNISHMENTS("get_target_punishments"),
        SEARCH_PUNISHMENTS("search_punishments"),
        VIEW_PUNISHMENT("view_punishment"),
        CREATE_PUNISHMENT("create_punishment"),
        REVOKE_PUNISHMENT("revoke_punishment"),
        ENFORCE_HOT_TARGET("enforce_hot_target")
    }

    private companion object {
        const val IDLE_POLL_MS = 200L
    }
}
