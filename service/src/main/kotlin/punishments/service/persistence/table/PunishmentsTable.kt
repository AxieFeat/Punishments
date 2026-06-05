package punishments.service.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object PunishmentsTable : Table("punishments") {
    val id = javaUUID("id")
    val type = varchar("type", 16)
    val status = varchar("status", 16)
    val selector = text("selector").nullable()
    val reasonId = varchar("reason_id", 64).nullable()
    val reasonText = text("reason_text").nullable()
    val issuedById = javaUUID("issued_by_id").nullable()
    val issuedByName = varchar("issued_by_name", 64)
    val issuedBySource = varchar("issued_by_source", 16)
    val issuedAtEpochMs = long("issued_at_epoch_ms")
    val expiresAtEpochMs = long("expires_at_epoch_ms").nullable()
    val revokedAtEpochMs = long("revoked_at_epoch_ms").nullable()
    val revokedById = javaUUID("revoked_by_id").nullable()
    val revokedByName = varchar("revoked_by_name", 64).nullable()
    val revokedBySource = varchar("revoked_by_source", 16).nullable()

    override val primaryKey = PrimaryKey(id)
}
