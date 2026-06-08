package punishments.common.model

/**
 * This interface represents an actor in the punishment system.
 * This is some entity, that can receive punishment, or issue/revoke it.
 *
 * @see ActorSource
 */
interface Actor {

    /**
     * Some string name of the actor. Just for display purposes. In example - auditing logs.
     */
    val name: String
}
