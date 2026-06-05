package punishments.service.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object PunishmentHistoryTable : Table("punishment_history") {
    val id = javaUUID("id")
    val punishmentId = javaUUID("punishment_id").references(PunishmentsTable.id)
    val type = varchar("type", 16)
    val actorId = javaUUID("actor_id").nullable()
    val actorName = varchar("actor_name", 64).nullable()
    val actorSource = varchar("actor_source", 16).nullable()
    val note = text("note").nullable()
    val timestampEpochMs = long("timestamp_epoch_ms")

    override val primaryKey = PrimaryKey(id)
}
