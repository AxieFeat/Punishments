package punishments.service.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object PunishmentScopesTable : Table("punishment_scopes") {
    val punishmentId = javaUUID("punishment_id").references(PunishmentRecordsTable.punishmentId)
    val restrictionKey = varchar("restriction_key", 128)

    override val primaryKey = PrimaryKey(punishmentId, restrictionKey)
}
