package punishments.client.stress.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExponentialPageDistributionTest {

    @Test
    fun `zero max page always returns first page`() {
        val distribution = ExponentialPageDistribution(maxPage = 0, lambda = 1.1)

        assertEquals(0, distribution.pick(0.99))
    }

    @Test
    fun `samples stay inside configured page bounds`() {
        val distribution = ExponentialPageDistribution(maxPage = 7, lambda = 0.8)

        val pages = listOf(0.0, 0.1, 0.5, 0.9, 0.999999).map(distribution::pick)

        assertTrue(pages.all { page -> page in 0..7 })
    }

    @Test
    fun `distribution favors earlier pages`() {
        val distribution = ExponentialPageDistribution(maxPage = 20, lambda = 1.2)

        assertTrue(distribution.pick(0.25) < distribution.pick(0.95))
    }
}
