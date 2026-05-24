package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.UUIDSerializer
import java.util.UUID

/**
 * Represents the actor who issued or revoked a punishment.
 */
@Serializable
data class PunishmentActor(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String,
    val source: ActorSource = ActorSource.STAFF
)
