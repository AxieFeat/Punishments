package punishments.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import punishments.common.config.PunishmentDefaults
import punishments.common.model.PunishmentTarget
import punishments.common.model.TargetKind

class ValidationUtilsTest {

    @Test
    fun `normalizes pagination bounds`() {
        assertEquals(0, ValidationUtils.normalizePage(-5))
        assertEquals(1, ValidationUtils.normalizePageSize(0))
        assertEquals(PunishmentDefaults.PAGE_SIZE, ValidationUtils.normalizePageSize(PunishmentDefaults.PAGE_SIZE + 1))
    }

    @Test
    fun `normalizes optional ids`() {
        assertNull(ValidationUtils.normalizeReasonId("   "))
        assertEquals("spam", ValidationUtils.normalizeReasonId("  spam  "))

        assertFailsWith<IllegalArgumentException> {
            ValidationUtils.normalizeRequestId("x".repeat(ValidationUtils.REQUEST_ID_MAX_LENGTH + 1))
        }
    }

    @Test
    fun `normalizes target names and removes duplicate targets`() {
        val targets = ValidationUtils.normalizeTargets(
            listOf(
                PunishmentTarget(name = "  Steve  ", targetType = TargetKind.PLAYER),
                PunishmentTarget(name = "steve", targetType = TargetKind.PLAYER),
                PunishmentTarget(name = "   ", targetType = TargetKind.PLAYER)
            )
        )

        assertEquals(listOf(PunishmentTarget(name = "Steve", targetType = TargetKind.PLAYER)), targets)
    }

    @Test
    fun `normalizes search query whitespace and case`() {
        assertEquals("ban appeal", ValidationUtils.normalizeSearchQuery("  BAN   Appeal  "))
    }
}
