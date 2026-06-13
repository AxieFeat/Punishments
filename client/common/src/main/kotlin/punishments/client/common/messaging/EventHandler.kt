package punishments.client.common.messaging

import punishments.common.event.PunishmentEvent

interface EventHandler {

    suspend fun onPunishmentCreated(event: PunishmentEvent.PunishmentCreated)
    suspend fun onPunishmentRevoked(event: PunishmentEvent.PunishmentRevoked)
    suspend fun onPunishmentExpired(event: PunishmentEvent.PunishmentExpired)
}
