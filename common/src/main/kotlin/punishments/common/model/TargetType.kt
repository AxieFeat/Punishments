package punishments.common.model

/**
 * @deprecated Use [TargetKind]. This object is kept as a source-compatible
 * bridge for code that still imports TargetType.PLAYER/UNKNOWN.
 */
@Deprecated("Use TargetKind instead", ReplaceWith("TargetKind"))
object TargetType {
    val PLAYER: TargetKind = TargetKind.PLAYER
    val IP_ADDRESS: TargetKind = TargetKind.IP_ADDRESS
    val UNKNOWN: TargetKind = TargetKind.UNKNOWN

    fun custom(name: String): TargetKind = TargetKind.custom(name)
}
