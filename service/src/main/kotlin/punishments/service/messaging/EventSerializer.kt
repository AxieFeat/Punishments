package punishments.service.messaging

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import punishments.common.event.EventType
import punishments.common.event.PunishmentEvent

object EventSerializer {

    fun serialize(event: PunishmentEvent, json: Json): Map<String, String> {
        return mapOf(
            "type" to event.type().name,
            "data" to json.encodeToString(event),
            "eventId" to event.metadata.eventId.toString(),
            "timestamp" to event.metadata.timestamp.toEpochMilliseconds().toString()
        )
    }

    private fun PunishmentEvent.type(): EventType {
        return when (this) {
            is PunishmentEvent.PunishmentCreated -> EventType.PUNISHMENT_CREATED
            is PunishmentEvent.PunishmentRevoked -> EventType.PUNISHMENT_REVOKED
            is PunishmentEvent.PunishmentExpired -> EventType.PUNISHMENT_EXPIRED
        }
    }
}
