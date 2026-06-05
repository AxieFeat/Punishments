package punishments.client.common.messaging

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class EventDeduplicator(
    private val maxSize: Int = 50_000,
    private val ttlMs: Long = 60_000L
) {
    private val processedIds = ConcurrentHashMap<UUID, Long>(maxSize, 0.75f, 4)

    fun tryProcess(eventId: UUID): Boolean {
        val now = System.currentTimeMillis()
        if (processedIds.size >= maxSize * 0.9) {
            evictExpired(now)
        }

        val previous = processedIds.putIfAbsent(eventId, now)
        return previous == null
    }

    private fun evictExpired(now: Long) {
        val iterator = processedIds.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > ttlMs) {
                iterator.remove()
            }
        }
    }
}
