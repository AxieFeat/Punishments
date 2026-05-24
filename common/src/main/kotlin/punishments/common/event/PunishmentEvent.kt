package punishments.common.event

import kotlinx.serialization.Serializable
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection
import punishments.common.serialization.UUIDSerializer
import java.util.UUID

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
        @Serializable(with = UUIDSerializer::class)
        val punishmentId: UUID,
        val type: PunishmentType,
        val selection: TargetSelection,
        val reasonId: String? = null,
        val actor: PunishmentActor
    ) : PunishmentEvent()

    /**
     * Emitted after a punishment is revoked.
     */
    @Serializable
    data class PunishmentRevoked(
        override val metadata: EventMetadata,
        @Serializable(with = UUIDSerializer::class)
        val punishmentId: UUID,
        val actor: PunishmentActor,
        val reason: String? = null
    ) : PunishmentEvent()

    /**
     * Emitted when a timed punishment expires.
     */
    @Serializable
    data class PunishmentExpired(
        override val metadata: EventMetadata,
        @Serializable(with = UUIDSerializer::class)
        val punishmentId: UUID
    ) : PunishmentEvent()
}
