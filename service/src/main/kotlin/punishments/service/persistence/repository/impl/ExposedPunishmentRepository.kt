package punishments.service.persistence.repository.impl

import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentHistoryEntry
import punishments.common.model.PunishmentHistoryType
import punishments.common.model.PunishmentRecord
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentSort
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentSummaryRecord
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection
import punishments.common.model.TargetKind
import punishments.common.util.TargetKeys
import punishments.service.persistence.DatabaseManager
import punishments.service.persistence.mapper.PunishmentMapper
import punishments.service.persistence.repository.ActiveRestrictionRecord
import punishments.service.persistence.repository.PunishmentRepository
import punishments.service.persistence.repository.RepositoryPage
import punishments.service.persistence.table.PunishmentActiveRestrictionsTable
import punishments.service.persistence.table.PunishmentIdempotencyRequestsTable
import punishments.service.persistence.table.PunishmentHistoryTable
import punishments.service.persistence.table.PunishmentScopesTable
import punishments.service.persistence.table.PunishmentTargetsTable
import punishments.service.persistence.table.PunishmentRecordsTable
import java.sql.SQLException
import java.util.UUID
import kotlin.time.Instant

class ActiveRestrictionConflictException(message: String) : IllegalStateException(message)

/**
 * PostgreSQL-backed repository for punishment records and enforcement projections.
 *
 * The main record is stored in `punishment_records`, while active BAN/MUTE state is
 * duplicated into `punishment_active_restrictions` so enforcement can do narrow,
 * indexed lookups without scanning historical rows.
 */
class ExposedPunishmentRepository(
    private val db: DatabaseManager
) : PunishmentRepository {

    override suspend fun insert(
        record: PunishmentRecord,
        selection: TargetSelection,
        historyEntry: PunishmentHistoryEntry
    ) {
        db.transaction {
            PunishmentRecordsTable.insert {
                it[punishmentId] = record.id
                it[punishmentType] = record.type.name
                it[punishmentStatus] = record.status.name
                it[targetSelector] = selection.selector
                it[punishmentReasonId] = record.reasonId
                it[punishmentReasonText] = record.reasonText
                it[issuerActorId] = record.issuedBy.id
                it[issuerActorName] = record.issuedBy.name
                it[issuerActorSource] = record.issuedBy.source.name
                it[issuedAtEpochMs] = record.issuedAt.toEpochMilliseconds()
                it[expiresAtEpochMs] = record.expiresAt?.toEpochMilliseconds()
                it[revokedAtEpochMs] = record.revokedAt?.toEpochMilliseconds()
                it[revokerActorId] = record.revokedBy?.id
                it[revokerActorName] = record.revokedBy?.name
                it[revokerActorSource] = record.revokedBy?.source?.name
            }
            insertTargets(record)
            insertScope(record)
            insertActiveRestrictions(record)
            insertHistory(historyEntry)
        }
    }

    override suspend fun getById(id: UUID): PunishmentRecord? {
        return db.transaction {
            PunishmentRecordsTable.selectAll()
                .where { PunishmentRecordsTable.punishmentId eq id }
                .firstOrNull()
                ?.let(::loadRecord)
        }
    }

    override suspend fun findActiveConflict(
        type: PunishmentType,
        target: PunishmentTarget,
        nowEpochMs: Long
    ): UUID? {
        return db.transaction {
            val targetIds = matchingPunishmentIds(listOf(target))
            if (targetIds.isEmpty()) {
                null
            } else {
                PunishmentRecordsTable.select(PunishmentRecordsTable.punishmentId)
                    .where {
                        (PunishmentRecordsTable.punishmentId inList targetIds) and
                            (PunishmentRecordsTable.punishmentType eq type.name) and
                            activeAt(nowEpochMs)
                    }
                    .limit(1)
                    .firstOrNull()
                    ?.get(PunishmentRecordsTable.punishmentId)
            }
        }
    }

    override suspend fun list(
        targets: List<PunishmentTarget>,
        type: PunishmentType?,
        status: PunishmentStatus?,
        sort: PunishmentSort,
        page: Int,
        pageSize: Int
    ): RepositoryPage<PunishmentSummaryRecord> {
        return db.transaction {
            val matchedIds = if (targets.isEmpty()) null else matchingPunishmentIds(targets)
            if (matchedIds != null && matchedIds.isEmpty()) {
                RepositoryPage(emptyList(), 0)
            } else {
                val query = PunishmentRecordsTable.selectAll().where {
                    listFilter(matchedIds, type, status)
                }
                val total = query.count()
                val rows = query.sorted(sort)
                    .limit(pageSize)
                    .offset(page.toLong() * pageSize)
                    .toList()
                val targetsByPunishmentId = loadTargets(rows.map { row -> row[PunishmentRecordsTable.punishmentId] })
                val items = rows.map { row -> loadSummary(row, targetsByPunishmentId) }
                RepositoryPage(items, total)
            }
        }
    }

    override suspend fun search(query: String, page: Int, pageSize: Int): RepositoryPage<PunishmentSummaryRecord> {
        return db.transaction {
            query.toUuidOrNull()?.let { punishmentId ->
                return@transaction searchById(punishmentId, page)
            }
            val matchedIds = searchTargetPunishmentIds(query)
            val searchOp = searchFilter(query, matchedIds)
            val dbQuery = PunishmentRecordsTable.selectAll().where { searchOp }
            val total = dbQuery.count()
            val rows = dbQuery.orderBy(PunishmentRecordsTable.issuedAtEpochMs to SortOrder.DESC)
                .limit(pageSize)
                .offset(page.toLong() * pageSize)
                .toList()
            val targetsByPunishmentId = loadTargets(rows.map { row -> row[PunishmentRecordsTable.punishmentId] })
            val items = rows.map { row -> loadSummary(row, targetsByPunishmentId) }
            RepositoryPage(items, total)
        }
    }

    private fun searchById(id: UUID, page: Int): RepositoryPage<PunishmentSummaryRecord> {
        val row = PunishmentRecordsTable.selectAll()
            .where { PunishmentRecordsTable.punishmentId eq id }
            .firstOrNull()
            ?: return RepositoryPage(emptyList(), 0)

        if (page > 0) {
            return RepositoryPage(emptyList(), 1)
        }

        val targetsByPunishmentId = loadTargets(listOf(row[PunishmentRecordsTable.punishmentId]))
        return RepositoryPage(listOf(loadSummary(row, targetsByPunishmentId)), 1)
    }

    override suspend fun revoke(
        id: UUID,
        actor: PunishmentActor,
        revokedAtEpochMs: Long,
        historyEntry: PunishmentHistoryEntry
    ): Boolean {
        return db.transaction {
            val updated = PunishmentRecordsTable.update({
                (PunishmentRecordsTable.punishmentId eq id) and
                    (PunishmentRecordsTable.punishmentStatus eq PunishmentStatus.ACTIVE.name)
            }) {
                it[punishmentStatus] = PunishmentStatus.REVOKED.name
                it[PunishmentRecordsTable.revokedAtEpochMs] = revokedAtEpochMs
                it[revokerActorId] = actor.id
                it[revokerActorName] = actor.name
                it[revokerActorSource] = actor.source.name
            }
            if (updated > 0) {
                deleteActiveRestrictions(id)
                insertHistory(historyEntry)
            }
            updated > 0
        }
    }

    override suspend fun expireDue(nowEpochMs: Long, limit: Int): List<PunishmentRecord> {
        return db.transaction {
            val rows = PunishmentRecordsTable.selectAll()
                .where {
                    (PunishmentRecordsTable.punishmentStatus eq PunishmentStatus.ACTIVE.name) and
                        PunishmentRecordsTable.expiresAtEpochMs.isNotNull() and
                        (PunishmentRecordsTable.expiresAtEpochMs lessEq nowEpochMs)
                }
                .orderBy(PunishmentRecordsTable.expiresAtEpochMs to SortOrder.ASC)
                .limit(limit)
                .forUpdate()
                .toList()

            rows.map { row ->
                val record = loadRecord(row).copy(status = PunishmentStatus.EXPIRED)
                PunishmentRecordsTable.update({ PunishmentRecordsTable.punishmentId eq record.id }) {
                    it[punishmentStatus] = PunishmentStatus.EXPIRED.name
                }
                deleteActiveRestrictions(record.id)
                insertHistory(
                    PunishmentHistoryEntry(
                        punishmentId = record.id,
                        type = PunishmentHistoryType.EXPIRED,
                        note = "Expired automatically",
                        timestamp = Instant.fromEpochMilliseconds(nowEpochMs)
                    )
                )
                record
            }
        }
    }

    override suspend fun findActiveRestrictions(
        targets: List<PunishmentTarget>,
        types: Set<PunishmentType>,
        restrictionKeys: Set<String>,
        nowEpochMs: Long
    ): List<ActiveRestrictionRecord> {
        if (targets.isEmpty()) {
            return emptyList()
        }

        return db.transaction {
            val targetKeys = targets.map(TargetKeys::normalized).distinct()
            val filters = buildList {
                add(PunishmentActiveRestrictionsTable.normalizedTargetKey inList targetKeys)
                add(activeRestrictionAt(nowEpochMs))
                if (types.isNotEmpty()) {
                    add(PunishmentActiveRestrictionsTable.punishmentType inList types.map(PunishmentType::name))
                }
                if (restrictionKeys.isNotEmpty()) {
                    add(PunishmentActiveRestrictionsTable.restrictionKey inList restrictionKeys)
                }
            }

            PunishmentActiveRestrictionsTable.selectAll()
                .where { filters.andAll() ?: Op.FALSE }
                .toList()
                .groupBy { row ->
                    ActiveRestrictionGroup(
                        punishmentId = row[PunishmentActiveRestrictionsTable.punishmentId],
                        type = PunishmentType.valueOf(row[PunishmentActiveRestrictionsTable.punishmentType]),
                        target = PunishmentTarget(
                            id = row[PunishmentActiveRestrictionsTable.targetId],
                            name = row[PunishmentActiveRestrictionsTable.targetName],
                            targetType = TargetKind.custom(row[PunishmentActiveRestrictionsTable.targetType])
                        ),
                        reasonId = row[PunishmentActiveRestrictionsTable.punishmentReasonId],
                        expiresAtEpochMs = row[PunishmentActiveRestrictionsTable.expiresAtEpochMs]
                    )
                }
                .map { (group, rows) ->
                    ActiveRestrictionRecord(
                        punishmentId = group.punishmentId,
                        type = group.type,
                        target = group.target,
                        restrictionKeys = rows.map { row -> row[PunishmentActiveRestrictionsTable.restrictionKey] }.toSet(),
                        reasonId = group.reasonId,
                        expiresAtEpochMs = group.expiresAtEpochMs
                    )
                }
        }
    }

    override suspend fun countActiveRestrictions(nowEpochMs: Long): Long {
        return db.transaction {
            PunishmentActiveRestrictionsTable.selectAll()
                .where { activeRestrictionAt(nowEpochMs) }
                .count()
        }
    }

    override suspend fun releaseActiveRestrictions(punishmentId: UUID) {
        db.transaction {
            deleteActiveRestrictions(punishmentId)
        }
    }

    override suspend fun findIdempotencyResult(operation: String, requestId: String, requestHash: String): String? {
        return db.transaction {
            val row = PunishmentIdempotencyRequestsTable.selectAll()
                .where {
                    (PunishmentIdempotencyRequestsTable.operation eq operation) and
                        (PunishmentIdempotencyRequestsTable.requestId eq requestId)
                }
                .firstOrNull() ?: return@transaction null

            if (row[PunishmentIdempotencyRequestsTable.requestHash] != requestHash) {
                throw IllegalArgumentException("requestId was already used with a different request body")
            }
            row[PunishmentIdempotencyRequestsTable.resultJson]
        }
    }

    override suspend fun storeIdempotencyResult(
        operation: String,
        requestId: String,
        requestHash: String,
        resultJson: String,
        createdAtEpochMs: Long
    ) {
        db.transaction {
            try {
                PunishmentIdempotencyRequestsTable.insert {
                    it[PunishmentIdempotencyRequestsTable.operation] = operation
                    it[PunishmentIdempotencyRequestsTable.requestId] = requestId
                    it[PunishmentIdempotencyRequestsTable.requestHash] = requestHash
                    it[PunishmentIdempotencyRequestsTable.resultJson] = resultJson
                    it[PunishmentIdempotencyRequestsTable.createdAtEpochMs] = createdAtEpochMs
                }
            } catch (e: Exception) {
                if (!e.isUniqueViolation()) {
                    throw e
                }
            }
        }
    }

    private fun insertTargets(record: PunishmentRecord) {
        record.targets.forEachIndexed { index, target ->
            PunishmentTargetsTable.insert {
                it[punishmentTargetId] = UUID.randomUUID()
                it[punishmentId] = record.id
                it[targetId] = target.id
                it[targetName] = target.name
                it[targetType] = target.targetType.name
                it[targetOrder] = index
            }
        }
    }

    private fun insertScope(record: PunishmentRecord) {
        record.scope.restrictionKeys.forEach { key ->
            PunishmentScopesTable.insert {
                it[punishmentId] = record.id
                it[restrictionKey] = key
            }
        }
    }

    private fun insertActiveRestrictions(record: PunishmentRecord) {
        if (!record.type.hasActiveRestriction() || record.status != PunishmentStatus.ACTIVE) {
            return
        }

        val restrictionKeys = record.scope.restrictionKeys.ifEmpty { setOf(TYPE_WIDE_RESTRICTION_KEY) }
        val createdAt = record.issuedAt.toEpochMilliseconds()

        record.targets.forEach { target ->
            val targetKey = TargetKeys.normalized(target)
            purgeExpiredActiveRestrictions(targetKey, record.type, createdAt)

            restrictionKeys.forEach { restrictionKey ->
                try {
                    // This unique insert is the authority-level conflict guard for
                    // concurrent BAN/MUTE creates across Envoy-routed replicas.
                    PunishmentActiveRestrictionsTable.insert {
                        it[activeRestrictionId] = UUID.randomUUID()
                        it[punishmentId] = record.id
                        it[PunishmentActiveRestrictionsTable.normalizedTargetKey] = targetKey
                        it[targetId] = target.id
                        it[targetName] = target.name
                        it[targetType] = target.targetType.name
                        it[punishmentType] = record.type.name
                        it[PunishmentActiveRestrictionsTable.restrictionKey] = restrictionKey
                        it[punishmentReasonId] = record.reasonId
                        it[expiresAtEpochMs] = record.expiresAt?.toEpochMilliseconds()
                        it[createdAtEpochMs] = createdAt
                    }
                } catch (e: Exception) {
                    if (e.isUniqueViolation()) {
                        throw ActiveRestrictionConflictException(
                            "Active ${record.type.name} already exists for ${target.targetType.name}:$targetKey"
                        )
                    }
                    throw e
                }
            }
        }
    }

    private fun purgeExpiredActiveRestrictions(targetKey: String, type: PunishmentType, nowEpochMs: Long) {
        PunishmentActiveRestrictionsTable.deleteWhere {
            (PunishmentActiveRestrictionsTable.normalizedTargetKey eq targetKey) and
                (PunishmentActiveRestrictionsTable.punishmentType eq type.name) and
                PunishmentActiveRestrictionsTable.expiresAtEpochMs.isNotNull() and
                (PunishmentActiveRestrictionsTable.expiresAtEpochMs lessEq nowEpochMs)
        }
    }

    private fun deleteActiveRestrictions(punishmentId: UUID) {
        PunishmentActiveRestrictionsTable.deleteWhere { PunishmentActiveRestrictionsTable.punishmentId eq punishmentId }
    }

    private fun insertHistory(entry: PunishmentHistoryEntry) {
        PunishmentHistoryTable.insert {
            it[historyEntryId] = entry.id
            it[punishmentId] = entry.punishmentId
            it[historyType] = entry.type.name
            it[actorId] = entry.actor?.id
            it[actorName] = entry.actor?.name
            it[actorSource] = entry.actor?.source?.name
            it[reasonText] = entry.note
            it[occurredAtEpochMs] = entry.timestamp.toEpochMilliseconds()
        }
    }

    private fun loadRecord(row: ResultRow): PunishmentRecord {
        val punishmentId = row[PunishmentRecordsTable.punishmentId]
        val targets = loadTargets(listOf(punishmentId))[punishmentId].orEmpty()
        val scope = PunishmentScope(
            PunishmentScopesTable.select(PunishmentScopesTable.restrictionKey)
                .where { PunishmentScopesTable.punishmentId eq punishmentId }
                .map { scopeRow -> scopeRow[PunishmentScopesTable.restrictionKey] }
                .toSet()
        )
        return PunishmentMapper.fromRow(row, targets, scope)
    }

    private fun loadSummary(
        row: ResultRow,
        targetsByPunishmentId: Map<UUID, List<PunishmentTarget>>
    ): PunishmentSummaryRecord {
        val punishmentId = row[PunishmentRecordsTable.punishmentId]
        return PunishmentMapper.summaryFromRow(row, targetsByPunishmentId[punishmentId].orEmpty())
    }

    private fun loadTargets(punishmentIds: List<UUID>): Map<UUID, List<PunishmentTarget>> {
        if (punishmentIds.isEmpty()) {
            return emptyMap()
        }

        return PunishmentTargetsTable.selectAll()
            .where { PunishmentTargetsTable.punishmentId inList punishmentIds }
            .orderBy(
                PunishmentTargetsTable.punishmentId to SortOrder.ASC,
                PunishmentTargetsTable.targetOrder to SortOrder.ASC
            )
            .map { targetRow ->
                val punishmentId = targetRow[PunishmentTargetsTable.punishmentId]
                val target = PunishmentTarget(
                    id = targetRow[PunishmentTargetsTable.targetId],
                    name = targetRow[PunishmentTargetsTable.targetName],
                    targetType = TargetKind.custom(targetRow[PunishmentTargetsTable.targetType])
                )
                punishmentId to target
            }
            .groupBy(
                keySelector = { (punishmentId, _) -> punishmentId },
                valueTransform = { (_, target) -> target }
            )
    }

    private fun matchingPunishmentIds(targets: List<PunishmentTarget>): List<UUID> {
        val targetFilter = targets.mapNotNull(::targetFilter).orAll() ?: return emptyList()
        return PunishmentTargetsTable.select(PunishmentTargetsTable.punishmentId)
            .where { targetFilter }
            .map { row -> row[PunishmentTargetsTable.punishmentId] }
            .distinct()
    }

    private fun searchTargetPunishmentIds(query: String): List<UUID> {
        val pattern = "%${query.sanitizeLike()}%"
        return PunishmentTargetsTable.select(PunishmentTargetsTable.punishmentId)
            .where { PunishmentTargetsTable.targetName ilike pattern }
            .map { row -> row[PunishmentTargetsTable.punishmentId] }
            .distinct()
    }

    private fun listFilter(
        ids: List<UUID>?,
        type: PunishmentType?,
        status: PunishmentStatus?
    ): Op<Boolean> {
        val filters = buildList {
            if (ids != null) {
                add(PunishmentRecordsTable.punishmentId inList ids)
            }
            if (type != null) {
                add(PunishmentRecordsTable.punishmentType eq type.name)
            }
            if (status != null) {
                add(PunishmentRecordsTable.punishmentStatus eq status.name)
            }
        }
        return filters.andAll() ?: Op.TRUE
    }

    private fun searchFilter(query: String, targetMatches: List<UUID>): Op<Boolean> {
        val pattern = "%${query.sanitizeLike()}%"
        val filters = buildList {
            query.toUuidOrNull()?.let { uuid -> add(PunishmentRecordsTable.punishmentId eq uuid) }
            add(PunishmentRecordsTable.punishmentReasonId ilike pattern)
            add(PunishmentRecordsTable.punishmentReasonText ilike pattern)
            add(PunishmentRecordsTable.issuerActorName ilike pattern)
            add(PunishmentRecordsTable.revokerActorName ilike pattern)
            if (targetMatches.isNotEmpty()) {
                add(PunishmentRecordsTable.punishmentId inList targetMatches)
            }
        }
        return filters.orAll() ?: Op.FALSE
    }

    private fun targetFilter(target: PunishmentTarget): Op<Boolean>? {
        val filters = buildList {
            target.id?.let { id -> add(PunishmentTargetsTable.targetId eq id) }
            target.name?.takeIf(String::isNotBlank)?.let { name ->
                add(
                    (PunishmentTargetsTable.targetName eq name) and
                        (PunishmentTargetsTable.targetType eq target.targetType.name)
                )
            }
        }
        return filters.orAll()
    }

    private fun activeAt(nowEpochMs: Long): Op<Boolean> {
        return (PunishmentRecordsTable.punishmentStatus eq PunishmentStatus.ACTIVE.name) and
            (PunishmentRecordsTable.expiresAtEpochMs.isNull() or (PunishmentRecordsTable.expiresAtEpochMs greater nowEpochMs))
    }

    private fun activeRestrictionAt(nowEpochMs: Long): Op<Boolean> {
        return PunishmentActiveRestrictionsTable.expiresAtEpochMs.isNull() or
            (PunishmentActiveRestrictionsTable.expiresAtEpochMs greater nowEpochMs)
    }

    private fun PunishmentType.hasActiveRestriction(): Boolean {
        return this == PunishmentType.BAN || this == PunishmentType.MUTE
    }

    private fun Query.sorted(sort: PunishmentSort): Query {
        return when (sort) {
            PunishmentSort.NEWEST -> orderBy(PunishmentRecordsTable.issuedAtEpochMs to SortOrder.DESC)
            PunishmentSort.OLDEST -> orderBy(PunishmentRecordsTable.issuedAtEpochMs to SortOrder.ASC)
            PunishmentSort.EXPIRES_SOON -> orderBy(PunishmentRecordsTable.expiresAtEpochMs to SortOrder.ASC)
        }
    }

    private fun String.sanitizeLike(): String {
        return replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    private fun String.toUuidOrNull(): UUID? {
        return try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun List<Op<Boolean>>.andAll(): Op<Boolean>? {
        return reduceOrNull { left, right -> left and right }
    }

    private fun List<Op<Boolean>>.orAll(): Op<Boolean>? {
        return reduceOrNull { left, right -> left or right }
    }

    private fun Exception.isUniqueViolation(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SQLException && current.sqlState == UNIQUE_VIOLATION_SQL_STATE) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private infix fun Expression<*>.ilike(pattern: String): Op<Boolean> {
        return object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append(this@ilike)
                queryBuilder.append(" ILIKE ")
                queryBuilder.registerArgument(TextColumnType(), pattern)
            }
        }
    }

    private data class ActiveRestrictionGroup(
        val punishmentId: UUID,
        val type: PunishmentType,
        val target: PunishmentTarget,
        val reasonId: String?,
        val expiresAtEpochMs: Long?
    )

    private companion object {
        const val TYPE_WIDE_RESTRICTION_KEY = "*"
        const val UNIQUE_VIOLATION_SQL_STATE = "23505"
    }
}
