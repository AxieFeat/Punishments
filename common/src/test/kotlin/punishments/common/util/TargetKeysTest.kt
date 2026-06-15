package punishments.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import punishments.common.model.PunishmentTarget
import punishments.common.model.TargetKind
import java.util.UUID

class TargetKeysTest {

    @Test
    fun `uses uuid as canonical target identity when present`() {
        val id = UUID.fromString("E7E09F79-3E0F-4DF7-92D9-83AE9F08A6E2")
        val target = PunishmentTarget(
            id = id,
            name = "DifferentName",
            targetType = TargetKind.PLAYER
        )

        assertEquals(
            "PLAYER:id:e7e09f79-3e0f-4df7-92d9-83ae9f08a6e2",
            TargetKeys.normalized(target)
        )
    }

    @Test
    fun `normalizes name targets case and whitespace`() {
        val target = PunishmentTarget(
            name = "  Steve_Player  ",
            targetType = TargetKind.PLAYER
        )

        assertEquals("PLAYER:name:steve_player", TargetKeys.normalized(target))
    }

    @Test
    fun `ip targets have explicit target kind`() {
        val target = PunishmentTarget.ipAddress("  127.0.0.1  ")

        assertEquals("IP_ADDRESS:name:127.0.0.1", TargetKeys.normalized(target))
    }
}
