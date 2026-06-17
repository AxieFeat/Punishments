package punishments.service.persistence.mapper

import org.jetbrains.exposed.v1.core.ResultRow
import punishments.common.model.ActorSource
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentRecord
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentSummaryRecord
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.service.persistence.table.PunishmentRecordsTable
import kotlin.time.Instant

object PunishmentMapper {

    fun fromRow(
        row: ResultRow,
        targets: List<PunishmentTarget>,
        scope: PunishmentScope
    ): PunishmentRecord {
        return PunishmentRecord(
            id = row[PunishmentRecordsTable.punishmentId],
            type = PunishmentType.valueOf(row[PunishmentRecordsTable.punishmentType]),
            status = PunishmentStatus.valueOf(row[PunishmentRecordsTable.punishmentStatus]),
            targets = targets,
            targetSelector = row[PunishmentRecordsTable.targetSelector],
            scope = scope,
            reasonId = row[PunishmentRecordsTable.punishmentReasonId],
            reasonText = row[PunishmentRecordsTable.punishmentReasonText],
            issuedBy = PunishmentActor(
                id = row[PunishmentRecordsTable.issuerActorId],
                name = row[PunishmentRecordsTable.issuerActorName],
                source = ActorSource.custom(row[PunishmentRecordsTable.issuerActorSource])
            ),
            issuedAt = row[PunishmentRecordsTable.issuedAtEpochMs].toInstant(),
            expiresAt = row[PunishmentRecordsTable.expiresAtEpochMs]?.toInstant(),
            revokedAt = row[PunishmentRecordsTable.revokedAtEpochMs]?.toInstant(),
            revokedBy = revokedBy(row)
        )
    }

    fun summaryFromRow(
        row: ResultRow,
        targets: List<PunishmentTarget>
    ): PunishmentSummaryRecord {
        return PunishmentSummaryRecord(
            id = row[PunishmentRecordsTable.punishmentId],
            type = PunishmentType.valueOf(row[PunishmentRecordsTable.punishmentType]),
            status = PunishmentStatus.valueOf(row[PunishmentRecordsTable.punishmentStatus]),
            targets = targets,
            reasonId = row[PunishmentRecordsTable.punishmentReasonId],
            reasonText = row[PunishmentRecordsTable.punishmentReasonText],
            issuedBy = issuedBy(row),
            issuedAt = row[PunishmentRecordsTable.issuedAtEpochMs].toInstant(),
            expiresAt = row[PunishmentRecordsTable.expiresAtEpochMs]?.toInstant()
        )
    }

    private fun issuedBy(row: ResultRow): PunishmentActor {
        return PunishmentActor(
            id = row[PunishmentRecordsTable.issuerActorId],
            name = row[PunishmentRecordsTable.issuerActorName],
            source = ActorSource.custom(row[PunishmentRecordsTable.issuerActorSource])
        )
    }

    private fun revokedBy(row: ResultRow): PunishmentActor? {
        val name = row[PunishmentRecordsTable.revokerActorName] ?: return null
        return PunishmentActor(
            id = row[PunishmentRecordsTable.revokerActorId],
            name = name,
            source = ActorSource.custom(row[PunishmentRecordsTable.revokerActorSource] ?: ActorSource.SYSTEM.name)
        )
    }

    private fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)
}
