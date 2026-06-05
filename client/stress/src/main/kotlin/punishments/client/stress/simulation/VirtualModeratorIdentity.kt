package punishments.client.stress.simulation

import java.util.UUID
import kotlin.random.Random

data class VirtualModeratorIdentity(
    val uuid: UUID = UUID.randomUUID(),
    val name: String
) {
    companion object {
        private val prefixes = listOf(
            "Mod", "Staff", "Guard", "Admin", "Trial", "Neo", "Sky", "Nether", "Ice", "Fire"
        )
        private val names = listOf(
            "Watcher", "Sentinel", "Phoenix", "Raven", "Knight", "Oracle", "Hammer", "Beacon", "Archer", "Echo"
        )
        private val suffixes = listOf("01", "02", "07", "11", "21", "99", "X", "HD", "MC", "")

        fun generateRandom(index: Int): VirtualModeratorIdentity {
            val raw = buildString {
                append(prefixes.random())
                append(names.random())
                if (Random.nextBoolean()) {
                    append(suffixes.random())
                }
                append(index % 10)
            }
            return VirtualModeratorIdentity(name = raw.take(16))
        }
    }
}
