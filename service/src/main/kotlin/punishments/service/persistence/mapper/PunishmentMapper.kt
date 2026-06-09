package punishments.service.persistence.mapper

import org.jetbrains.exposed.v1.core.ResultRow
import punishments.common.model.ActorSourceType
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentRecord
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentSummaryRecord
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.dto.ActorTypeDto
import punishments.service.persistence.table.PunishmentsTable
import kotlin.time.Instant

object PunishmentMapper {

    fun fromRow(
        row: ResultRow,
        targets: List<PunishmentTarget>,
        scope: PunishmentScope
    ): PunishmentRecord {
        return PunishmentRecord(
            id = row[PunishmentsTable.id],
            type = PunishmentType.valueOf(row[PunishmentsTable.type]),
            status = PunishmentStatus.valueOf(row[PunishmentsTable.status]),
            targets = targets,
            scope = scope,
            reasonId = row[PunishmentsTable.reasonId],
            reasonText = row[PunishmentsTable.reasonText],
            issuedBy = PunishmentActor(
                id = row[PunishmentsTable.issuedById],
                name = row[PunishmentsTable.issuedByName],
                source = ActorTypeDto(row[PunishmentsTable.issuedBySource])
            ),
            issuedAt = row[PunishmentsTable.issuedAtEpochMs].toInstant(),
            expiresAt = row[PunishmentsTable.expiresAtEpochMs]?.toInstant(),
            revokedAt = row[PunishmentsTable.revokedAtEpochMs]?.toInstant(),
            revokedBy = revokedBy(row)
        )
    }

    fun summaryFromRow(
        row: ResultRow,
        targets: List<PunishmentTarget>
    ): PunishmentSummaryRecord {
        return PunishmentSummaryRecord(
            id = row[PunishmentsTable.id],
            type = PunishmentType.valueOf(row[PunishmentsTable.type]),
            status = PunishmentStatus.valueOf(row[PunishmentsTable.status]),
            targets = targets,
            reasonId = row[PunishmentsTable.reasonId],
            reasonText = row[PunishmentsTable.reasonText],
            issuedBy = issuedBy(row),
            issuedAt = row[PunishmentsTable.issuedAtEpochMs].toInstant(),
            expiresAt = row[PunishmentsTable.expiresAtEpochMs]?.toInstant()
        )
    }

    private fun issuedBy(row: ResultRow): PunishmentActor {
        return PunishmentActor(
            id = row[PunishmentsTable.issuedById],
            name = row[PunishmentsTable.issuedByName],
            source = ActorTypeDto(row[PunishmentsTable.issuedBySource])
        )
    }

    private fun revokedBy(row: ResultRow): PunishmentActor? {
        val name = row[PunishmentsTable.revokedByName] ?: return null
        return PunishmentActor(
            id = row[PunishmentsTable.revokedById],
            name = name,
            source = ActorTypeDto(row[PunishmentsTable.revokedBySource] ?: ActorSourceType.SYSTEM.name)
        )
    }

    private fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)
}
