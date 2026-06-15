package punishments.service.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object PunishmentHistoryTable : Table("punishment_history") {
    val historyEntryId = javaUUID("history_entry_id")
    val punishmentId = javaUUID("punishment_id").references(PunishmentRecordsTable.punishmentId)
    val historyType = varchar("history_type", 16)
    val actorId = javaUUID("actor_id").nullable()
    val actorName = varchar("actor_name", 64).nullable()
    val actorSource = varchar("actor_source", 16).nullable()
    val reasonText = text("reason_text").nullable()
    val occurredAtEpochMs = long("occurred_at_epoch_ms")

    override val primaryKey = PrimaryKey(historyEntryId)
}
