package punishments.common.model

import kotlinx.serialization.Serializable
import punishments.common.serialization.ActorSourceSerializer
import java.util.Locale

/**
 * Source that issued or revoked a punishment.
 *
 * This is a serializable value object instead of an enum/interface pair, so
 * built-in and custom sources compare symmetrically after deserialization.
 */
@Serializable(with = ActorSourceSerializer::class)
class ActorSource private constructor(val name: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActorSource) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name

    companion object {

        val STAFF = ActorSource("STAFF")
        val CONSOLE = ActorSource("CONSOLE")
        val SYSTEM = ActorSource("SYSTEM")

        fun custom(name: String): ActorSource = ActorSource(normalizeTypeName(name))

        internal fun fromSerialized(name: String): ActorSource = custom(name)
    }
}

private fun normalizeTypeName(name: String): String {
    val normalized = name.trim().uppercase(Locale.ROOT)
    require(normalized.isNotEmpty()) { "Type name must not be blank" }
    return normalized
}
