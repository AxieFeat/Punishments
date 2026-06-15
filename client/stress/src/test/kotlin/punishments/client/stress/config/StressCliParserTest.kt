package punishments.client.stress.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StressCliParserTest {

    @Test
    fun `players and spawn rate are preserved in parsed config`() {
        val result = StressCliParser.parse(
            arrayOf(
                "--players", "37",
                "--spawn-rate", "9",
                "--steady", "12",
                "--warm-up", "0",
                "--ramp-up", "0",
                "--spike", "0",
                "--cooldown", "0"
            )
        )

        val config = assertIs<StressCliParseResult.Run>(result).config
        assertEquals(37, config.totalPlayers)
        assertEquals(9, config.spawnRatePerSecond)
        assertEquals(12, config.totalDurationSeconds)
    }

    @Test
    fun `parses profile hosts and metrics urls`() {
        val result = StressCliParser.parse(
            arrayOf(
                "--profile", "enforcement-heavy",
                "--hosts", "a:9090,b:9090",
                "--metrics-urls", "http://a:8080/metrics,http://b:8080/metrics"
            )
        )

        val config = assertIs<StressCliParseResult.Run>(result).config
        assertEquals(WorkloadProfile.ENFORCEMENT_HEAVY, config.profile)
        assertEquals(listOf("a:9090", "b:9090"), config.serviceAddresses)
        assertEquals(listOf("http://a:8080/metrics", "http://b:8080/metrics"), config.metricsUrls)
    }
}
