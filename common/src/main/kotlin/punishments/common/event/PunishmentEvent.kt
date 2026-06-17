package punishments.common.event

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentRecord

/**
 * Domain events emitted by the punishment service.
 */
@Serializable
sealed class PunishmentEvent {

    abstract val metadata: EventMetadata

    /**
     * Emitted after a punishment is created.
     */
    @Serializable
    data class PunishmentCreated(
        override val metadata: EventMetadata,
        val punishment: PunishmentRecord
    ) : PunishmentEvent()

    /**
     * Emitted after a punishment is revoked.
     */
    @Serializable
    data class PunishmentRevoked(
        override val metadata: EventMetadata,
        val punishment: PunishmentRecord
    ) : PunishmentEvent()

    /**
     * Emitted when a timed punishment expires.
     */
    @Serializable
    data class PunishmentExpired(
        override val metadata: EventMetadata,
        val punishment: PunishmentRecord
    ) : PunishmentEvent()
}
