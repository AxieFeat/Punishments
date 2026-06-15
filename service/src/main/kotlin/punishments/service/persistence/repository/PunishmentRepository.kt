package punishments.service.persistence.repository

import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentHistoryEntry
import punishments.common.model.PunishmentRecord
import punishments.common.model.PunishmentSort
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentSummaryRecord
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection
import java.util.UUID

interface PunishmentRepository {

    suspend fun insert(
        record: PunishmentRecord,
        selection: TargetSelection,
        historyEntry: PunishmentHistoryEntry
    )

    suspend fun getById(id: UUID): PunishmentRecord?

    suspend fun findActiveConflict(
        type: PunishmentType,
        target: PunishmentTarget,
        nowEpochMs: Long
    ): UUID?

    suspend fun list(
        targets: List<PunishmentTarget>,
        type: PunishmentType?,
        status: PunishmentStatus?,
        sort: PunishmentSort,
        page: Int,
        pageSize: Int
    ): RepositoryPage<PunishmentSummaryRecord>

    suspend fun search(query: String, page: Int, pageSize: Int): RepositoryPage<PunishmentSummaryRecord>

    suspend fun revoke(
        id: UUID,
        actor: PunishmentActor,
        revokedAtEpochMs: Long,
        historyEntry: PunishmentHistoryEntry
    ): Boolean

    suspend fun expireDue(nowEpochMs: Long, limit: Int): List<PunishmentRecord>

    suspend fun findActiveRestrictions(
        targets: List<PunishmentTarget>,
        types: Set<PunishmentType>,
        restrictionKeys: Set<String>,
        nowEpochMs: Long
    ): List<ActiveRestrictionRecord>

    suspend fun countActiveRestrictions(nowEpochMs: Long): Long

    suspend fun releaseActiveRestrictions(punishmentId: UUID)

    suspend fun findIdempotencyResult(operation: String, requestId: String, requestHash: String): String?

    suspend fun storeIdempotencyResult(
        operation: String,
        requestId: String,
        requestHash: String,
        resultJson: String,
        createdAtEpochMs: Long
    )
}

data class RepositoryPage<T>(
    val items: List<T>,
    val totalItems: Long
)
