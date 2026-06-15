package punishments.service.persistence.table

import org.jetbrains.exposed.v1.core.Table

object PunishmentIdempotencyRequestsTable : Table("punishment_idempotency_requests") {
    val operation = varchar("operation", 32)
    val requestId = varchar("request_id", 128)
    val requestHash = varchar("request_hash", 64)
    val resultJson = text("result_json")
    val createdAtEpochMs = long("created_at_epoch_ms")

    override val primaryKey = PrimaryKey(operation, requestId)
}
