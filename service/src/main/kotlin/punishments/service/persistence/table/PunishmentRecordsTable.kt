package punishments.service.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object PunishmentRecordsTable : Table("punishment_records") {
    val punishmentId = javaUUID("punishment_id")
    val punishmentType = varchar("punishment_type", 16)
    val punishmentStatus = varchar("punishment_status", 16)
    val targetSelector = text("target_selector").nullable()
    val punishmentReasonId = varchar("punishment_reason_id", 64).nullable()
    val punishmentReasonText = text("punishment_reason_text").nullable()
    val issuerActorId = javaUUID("issuer_actor_id").nullable()
    val issuerActorName = varchar("issuer_actor_name", 64)
    val issuerActorSource = varchar("issuer_actor_source", 16)
    val issuedAtEpochMs = long("issued_at_epoch_ms")
    val expiresAtEpochMs = long("expires_at_epoch_ms").nullable()
    val revokedAtEpochMs = long("revoked_at_epoch_ms").nullable()
    val revokerActorId = javaUUID("revoker_actor_id").nullable()
    val revokerActorName = varchar("revoker_actor_name", 64).nullable()
    val revokerActorSource = varchar("revoker_actor_source", 16).nullable()

    override val primaryKey = PrimaryKey(punishmentId)
}
