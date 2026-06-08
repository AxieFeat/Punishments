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
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import punishments.common.dto.ActorDto
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentHistoryEntry
import punishments.common.model.PunishmentHistoryType
import punishments.common.model.PunishmentRecord
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentSort
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection
import punishments.service.persistence.DatabaseManager
import punishments.service.persistence.mapper.PunishmentMapper
import punishments.service.persistence.repository.PunishmentRepository
import punishments.service.persistence.repository.RepositoryPage
import punishments.service.persistence.table.PunishmentHistoryTable
import punishments.service.persistence.table.PunishmentScopesTable
import punishments.service.persistence.table.PunishmentTargetsTable
import punishments.service.persistence.table.PunishmentsTable
import java.util.UUID
import kotlin.time.Instant

class ExposedPunishmentRepository(
    private val db: DatabaseManager
) : PunishmentRepository {

    override suspend fun insert(
        record: PunishmentRecord,
        selection: TargetSelection,
        historyEntry: PunishmentHistoryEntry
    ) {
        db.transaction {
            PunishmentsTable.insert {
                it[id] = record.id
                it[type] = record.type.name
                it[status] = record.status.name
                it[selector] = selection.selector
                it[reasonId] = record.reasonId
                it[reasonText] = record.reasonText
                it[issuedById] = record.issuedBy.id
                it[issuedByName] = record.issuedBy.name
                it[issuedBySource] = record.issuedBy.source.name
                it[issuedAtEpochMs] = record.issuedAt.toEpochMilliseconds()
                it[expiresAtEpochMs] = record.expiresAt?.toEpochMilliseconds()
                it[revokedAtEpochMs] = record.revokedAt?.toEpochMilliseconds()
                it[revokedById] = record.revokedBy?.id
                it[revokedByName] = record.revokedBy?.name
                it[revokedBySource] = record.revokedBy?.source?.name
            }
            insertTargets(record)
            insertScope(record)
            insertHistory(historyEntry)
        }
    }

    override suspend fun getById(id: UUID): PunishmentRecord? {
        return db.transaction {
            PunishmentsTable.selectAll()
                .where { PunishmentsTable.id eq id }
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
                PunishmentsTable.select(PunishmentsTable.id)
                    .where {
                        (PunishmentsTable.id inList targetIds) and
                            (PunishmentsTable.type eq type.name) and
                            activeAt(nowEpochMs)
                    }
                    .limit(1)
                    .firstOrNull()
                    ?.get(PunishmentsTable.id)
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
    ): RepositoryPage<PunishmentRecord> {
        return db.transaction {
            val matchedIds = if (targets.isEmpty()) null else matchingPunishmentIds(targets)
            if (matchedIds != null && matchedIds.isEmpty()) {
                RepositoryPage(emptyList(), 0)
            } else {
                val query = PunishmentsTable.selectAll().where {
                    listFilter(matchedIds, type, status)
                }
                val total = query.count()
                val items = query.sorted(sort)
                    .limit(pageSize)
                    .offset(page.toLong() * pageSize)
                    .map(::loadRecord)
                RepositoryPage(items, total)
            }
        }
    }

    override suspend fun search(query: String, page: Int, pageSize: Int): RepositoryPage<PunishmentRecord> {
        return db.transaction {
            val matchedIds = searchTargetPunishmentIds(query)
            val searchOp = searchFilter(query, matchedIds)
            val dbQuery = PunishmentsTable.selectAll().where { searchOp }
            val total = dbQuery.count()
            val items = dbQuery.orderBy(PunishmentsTable.issuedAtEpochMs to SortOrder.DESC)
                .limit(pageSize)
                .offset(page.toLong() * pageSize)
                .map(::loadRecord)
            RepositoryPage(items, total)
        }
    }

    override suspend fun revoke(
        id: UUID,
        actor: PunishmentActor,
        revokedAtEpochMs: Long,
        historyEntry: PunishmentHistoryEntry
    ): Boolean {
        return db.transaction {
            val updated = PunishmentsTable.update({
                (PunishmentsTable.id eq id) and (PunishmentsTable.status eq PunishmentStatus.ACTIVE.name)
            }) {
                it[status] = PunishmentStatus.REVOKED.name
                it[PunishmentsTable.revokedAtEpochMs] = revokedAtEpochMs
                it[revokedById] = actor.id
                it[revokedByName] = actor.name
                it[revokedBySource] = actor.source.name
            }
            if (updated > 0) {
                insertHistory(historyEntry)
            }
            updated > 0
        }
    }

    override suspend fun expireDue(nowEpochMs: Long, limit: Int): List<PunishmentRecord> {
        return db.transaction {
            val rows = PunishmentsTable.selectAll()
                .where {
                    (PunishmentsTable.status eq PunishmentStatus.ACTIVE.name) and
                        PunishmentsTable.expiresAtEpochMs.isNotNull() and
                        (PunishmentsTable.expiresAtEpochMs lessEq nowEpochMs)
                }
                .orderBy(PunishmentsTable.expiresAtEpochMs to SortOrder.ASC)
                .limit(limit)
                .forUpdate()
                .toList()

            rows.map { row ->
                val record = loadRecord(row).copy(status = PunishmentStatus.EXPIRED)
                PunishmentsTable.update({ PunishmentsTable.id eq record.id }) {
                    it[status] = PunishmentStatus.EXPIRED.name
                }
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

    private fun insertTargets(record: PunishmentRecord) {
        record.targets.forEachIndexed { index, target ->
            PunishmentTargetsTable.insert {
                it[id] = UUID.randomUUID()
                it[punishmentId] = record.id
                it[targetId] = target.id
                it[targetName] = target.name
                it[targetKind] = target.kind.name
                it[ordinal] = index
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

    private fun insertHistory(entry: PunishmentHistoryEntry) {
        PunishmentHistoryTable.insert {
            it[id] = entry.id
            it[punishmentId] = entry.punishmentId
            it[type] = entry.type.name
            it[actorId] = entry.actor?.id
            it[actorName] = entry.actor?.name
            it[actorSource] = entry.actor?.source?.name
            it[note] = entry.note
            it[timestampEpochMs] = entry.timestamp.toEpochMilliseconds()
        }
    }

    private fun loadRecord(row: ResultRow): PunishmentRecord {
        val punishmentId = row[PunishmentsTable.id]
        val targets = PunishmentTargetsTable.selectAll()
            .where { PunishmentTargetsTable.punishmentId eq punishmentId }
            .orderBy(PunishmentTargetsTable.ordinal to SortOrder.ASC)
            .map { targetRow ->
                PunishmentTarget(
                    id = targetRow[PunishmentTargetsTable.targetId],
                    name = targetRow[PunishmentTargetsTable.targetName],
                    kind = ActorDto(targetRow[PunishmentTargetsTable.targetKind])
                )
            }
        val scope = PunishmentScope(
            PunishmentScopesTable.select(PunishmentScopesTable.restrictionKey)
                .where { PunishmentScopesTable.punishmentId eq punishmentId }
                .map { scopeRow -> scopeRow[PunishmentScopesTable.restrictionKey] }
                .toSet()
        )
        return PunishmentMapper.fromRow(row, targets, scope)
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
                add(PunishmentsTable.id inList ids)
            }
            if (type != null) {
                add(PunishmentsTable.type eq type.name)
            }
            if (status != null) {
                add(PunishmentsTable.status eq status.name)
            }
        }
        return filters.andAll() ?: Op.TRUE
    }

    private fun searchFilter(query: String, targetMatches: List<UUID>): Op<Boolean> {
        val pattern = "%${query.sanitizeLike()}%"
        val filters = buildList {
            query.toUuidOrNull()?.let { uuid -> add(PunishmentsTable.id eq uuid) }
            add(PunishmentsTable.reasonId ilike pattern)
            add(PunishmentsTable.reasonText ilike pattern)
            add(PunishmentsTable.issuedByName ilike pattern)
            add(PunishmentsTable.revokedByName ilike pattern)
            if (targetMatches.isNotEmpty()) {
                add(PunishmentsTable.id inList targetMatches)
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
                        (PunishmentTargetsTable.targetKind eq target.kind.name)
                )
            }
        }
        return filters.orAll()
    }

    private fun activeAt(nowEpochMs: Long): Op<Boolean> {
        return (PunishmentsTable.status eq PunishmentStatus.ACTIVE.name) and
            (PunishmentsTable.expiresAtEpochMs.isNull() or (PunishmentsTable.expiresAtEpochMs greater nowEpochMs))
    }

    private fun Query.sorted(sort: PunishmentSort): Query {
        return when (sort) {
            PunishmentSort.NEWEST -> orderBy(PunishmentsTable.issuedAtEpochMs to SortOrder.DESC)
            PunishmentSort.OLDEST -> orderBy(PunishmentsTable.issuedAtEpochMs to SortOrder.ASC)
            PunishmentSort.EXPIRES_SOON -> orderBy(PunishmentsTable.expiresAtEpochMs to SortOrder.ASC)
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

    private infix fun Expression<*>.ilike(pattern: String): Op<Boolean> {
        return object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append(this@ilike)
                queryBuilder.append(" ILIKE ")
                queryBuilder.registerArgument(TextColumnType(), pattern)
            }
        }
    }
}
