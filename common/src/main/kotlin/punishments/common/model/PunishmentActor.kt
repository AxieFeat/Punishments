package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.ContextualUUID

/**
 * Represents the actor who issued or revoked a punishment.
 *
 * @param id The unique identifier of the actor, if applicable. For player actors, this would be their UUID. For console or other non-player actors, this can be null.
 * @param name The name of the actor. For player actors, this would be their username. For console or other non-player actors, this could be a descriptive name like "CONSOLE" or "EXTERNAL_SYSTEM".
 * @param source The source of the actor, which indicates whether the punishment was issued by a staff member, the console, or an external system.
 */
@Serializable
data class PunishmentActor(
    val id: ContextualUUID? = null,
    val name: String,
    val source: ActorSource = ActorSource.STAFF
) {
    companion object {
        fun staff(name: String, id: java.util.UUID? = null): PunishmentActor {
            return PunishmentActor(id = id, name = name, source = ActorSource.STAFF)
        }

        fun console(name: String = "CONSOLE"): PunishmentActor {
            return PunishmentActor(name = name, source = ActorSource.CONSOLE)
        }

        fun system(name: String = "SYSTEM", id: java.util.UUID? = null): PunishmentActor {
            return PunishmentActor(id = id, name = name, source = ActorSource.SYSTEM)
        }
    }
}
