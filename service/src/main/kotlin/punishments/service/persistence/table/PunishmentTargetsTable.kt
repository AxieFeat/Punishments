package punishments.service.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object PunishmentTargetsTable : Table("punishment_targets") {
    val id = javaUUID("id")
    val punishmentId = javaUUID("punishment_id").references(PunishmentsTable.id)
    val targetId = javaUUID("target_id").nullable()
    val targetName = varchar("target_name", 128).nullable()
    val targetKind = varchar("target_kind", 16)
    val ordinal = integer("ordinal")

    override val primaryKey = PrimaryKey(id)
}
