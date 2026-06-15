package punishments.client.stress.simulation

import java.util.UUID
import kotlin.random.Random

data class VirtualModeratorIdentity(
    val workerIndex: Int,
    val serverId: String,
    val uuid: UUID = UUID.randomUUID(),
    val name: String
) {
    companion object {
        private val prefixes = listOf("Mod", "Guard", "Admin", "Trial", "Signal", "Orbit")
        private val names = listOf("Echo", "Sentinel", "Beacon", "Raven", "Vector", "Cipher")
        private val suffixes = listOf("01", "02", "07", "11", "21", "99", "X", "HD", "")

        fun generate(index: Int, serverId: String): VirtualModeratorIdentity {
            val shortServer = serverId.removePrefix("stress-").takeLast(6)
            val rawName = buildString {
                append(prefixes.random())
                append(names.random())
                if (Random.nextBoolean()) {
                    append(suffixes.random())
                }
                append(shortServer.take(2))
                append(index % 10)
            }
            return VirtualModeratorIdentity(
                workerIndex = index,
                serverId = serverId,
                name = rawName.take(20)
            )
        }
    }
}
