package punishments.common.model

/**
 * This interface represents an actor in the punishment system.
 * This is some entity, that can receive punishment, or issue/revoke it.
 *
 * IMPORTANT: Any parameter in custom implementation except [name] will not be serialized.
 * And after deserialization in all cases you receive instance of [punishments.common.dto.ActorDto].
 * That's mean you have limitation in equality operation.
 *
 * In example:
 * ```kotlin
 * import punishments.common.model.Actor
 * import punishments.common.model.ActorSource
 *
 * val punishmentTarget: PunishmentTarget = ... // Target from service response.
 * val kind: Actor = punishmentTarget.kind
 * val isStaff: Boolean = kind == ActorSource.STAFF // Will be false in any way.
 *
 *
 * // But:
 * val staffSource: Actor = ActorSource.STAFF // <- For equality type should be Actor, not ActorSource.
 * val isStaff: Boolean = kind == staffSource // Can be true (If kind is really STAFF)
 *
 * // ***BUT*** VERY IMPORTANT!!:
 * val staffSource: Actor = ActorSource.STAFF
 * val isStaff: Boolean = staffSource == kind // WILL BE ALWAYS FALSE!!!!
 *
 * // For comparison you also can use names:
 * val isStaff: Boolean = kind.name == ActorSource.STAFF.name
 * ```
 *
 * @see ActorSource
 * @see TargetKind
 */
interface Actor {

    /**
     * Some string name of the actor. Just for display purposes. In example - auditing logs.
     */
    val name: String
}
