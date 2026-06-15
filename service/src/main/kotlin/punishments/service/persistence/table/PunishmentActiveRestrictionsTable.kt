package punishments.service.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object PunishmentActiveRestrictionsTable : Table("punishment_active_restrictions") {
    val activeRestrictionId = javaUUID("active_restriction_id")
    val punishmentId = javaUUID("punishment_id").references(PunishmentRecordsTable.punishmentId)
    val normalizedTargetKey = varchar("normalized_target_key", 192)
    val targetId = javaUUID("target_id").nullable()
    val targetName = varchar("target_name", 128).nullable()
    val targetType = varchar("target_type", 32)
    val punishmentType = varchar("punishment_type", 16)
    val restrictionKey = varchar("restriction_key", 128)
    val punishmentReasonId = varchar("punishment_reason_id", 64).nullable()
    val expiresAtEpochMs = long("expires_at_epoch_ms").nullable()
    val createdAtEpochMs = long("created_at_epoch_ms")

    override val primaryKey = PrimaryKey(activeRestrictionId)
}
