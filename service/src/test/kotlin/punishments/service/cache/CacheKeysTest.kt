package punishments.service.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetKind
import java.util.UUID

class CacheKeysTest {

    @Test
    fun `active restriction key is stable for unordered target and filter sets`() {
        val first = PunishmentTarget(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            name = "Alpha",
            targetType = TargetKind.PLAYER
        )
        val second = PunishmentTarget(
            id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            name = "Beta",
            targetType = TargetKind.PLAYER
        )

        val left = CacheKeys.activeRestrictions(
            targets = listOf(first, second),
            types = setOf(PunishmentType.BAN, PunishmentType.MUTE),
            restrictionKeys = setOf("chat", "login")
        )
        val right = CacheKeys.activeRestrictions(
            targets = listOf(second, first),
            types = setOf(PunishmentType.MUTE, PunishmentType.BAN),
            restrictionKeys = setOf("login", "chat")
        )

        assertEquals(left, right)
    }

    @Test
    fun `active restriction key separates restriction filters`() {
        val target = PunishmentTarget(name = "Target", targetType = TargetKind.PLAYER)

        assertNotEquals(
            CacheKeys.activeRestrictions(listOf(target), setOf(PunishmentType.MUTE), setOf("chat")),
            CacheKeys.activeRestrictions(listOf(target), setOf(PunishmentType.MUTE), setOf("login"))
        )
    }

    @Test
    fun `search key normalizes case and whitespace`() {
        assertEquals(
            CacheKeys.search("Ban Appeal", page = 0, pageSize = 50),
            CacheKeys.search("  ban   appeal  ", page = 0, pageSize = 50)
        )
    }

    @Test
    fun `records revision key is stable`() {
        assertEquals("punishment-service:revision:records", CacheKeys.recordsRevision())
    }

    @Test
    fun `target id revision is independent from target kind`() {
        val id = UUID.fromString("E7E09F79-3E0F-4DF7-92D9-83AE9F08A6E2")

        assertEquals(
            "punishment-service:revision:target-id:e7e09f79-3e0f-4df7-92d9-83ae9f08a6e2",
            CacheKeys.targetIdRevision(id)
        )
    }

    @Test
    fun `target name revision normalizes case and whitespace`() {
        assertEquals(
            "punishment-service:revision:target-name:PLAYER:steve",
            CacheKeys.targetNameRevision(TargetKind.PLAYER, "  Steve  ")
        )
    }
}
