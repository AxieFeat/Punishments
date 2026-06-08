package punishments.service.persistence.mapper

import org.jetbrains.exposed.v1.core.ResultRow
import punishments.common.model.ActorSource
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentRecord
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentStatus
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.dto.ActorDto
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
                source = ActorDto(row[PunishmentsTable.issuedBySource])
            ),
            issuedAt = row[PunishmentsTable.issuedAtEpochMs].toInstant(),
            expiresAt = row[PunishmentsTable.expiresAtEpochMs]?.toInstant(),
            revokedAt = row[PunishmentsTable.revokedAtEpochMs]?.toInstant(),
            revokedBy = revokedBy(row)
        )
    }

    private fun revokedBy(row: ResultRow): PunishmentActor? {
        val name = row[PunishmentsTable.revokedByName] ?: return null
        return PunishmentActor(
            id = row[PunishmentsTable.revokedById],
            name = name,
            source = ActorDto(row[PunishmentsTable.revokedBySource] ?: ActorSource.SYSTEM.name)
        )
    }

    private fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)
}
