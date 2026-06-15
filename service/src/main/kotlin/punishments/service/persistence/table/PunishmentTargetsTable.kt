package punishments.service.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object PunishmentTargetsTable : Table("punishment_targets") {
    val punishmentTargetId = javaUUID("punishment_target_id")
    val punishmentId = javaUUID("punishment_id").references(PunishmentRecordsTable.punishmentId)
    val targetId = javaUUID("target_id").nullable()
    val targetName = varchar("target_name", 128).nullable()
    val targetType = varchar("target_type", 16)
    val targetOrder = integer("target_order")

    override val primaryKey = PrimaryKey(punishmentTargetId)
}
