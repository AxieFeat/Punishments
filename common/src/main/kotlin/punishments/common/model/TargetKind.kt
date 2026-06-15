package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.TargetKindSerializer
import java.util.Locale

/**
 * Kind of entity a punishment targets.
 *
 * The service treats target kinds as open values: clients can use the built-in
 * constants or define their own without changing persistence or domain logic.
 */
@Serializable(with = TargetKindSerializer::class)
class TargetKind private constructor(val name: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TargetKind) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name

    companion object {
        val PLAYER = TargetKind("PLAYER")
        val IP_ADDRESS = TargetKind("IP_ADDRESS")
        val UNKNOWN = TargetKind("UNKNOWN")

        fun custom(name: String): TargetKind = TargetKind(normalizeTypeName(name))

        internal fun fromSerialized(name: String): TargetKind = custom(name)
    }
}

private fun normalizeTypeName(name: String): String {
    val normalized = name.trim().uppercase(Locale.ROOT)
    require(normalized.isNotEmpty()) { "Type name must not be blank" }
    return normalized
}
