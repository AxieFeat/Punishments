package punishments.client.stress

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import punishments.client.stress.config.StressCliParseResult
import punishments.client.stress.config.StressCliParser
import punishments.client.stress.simulation.SimulationEngine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (val parseResult = parseOrExit(args)) {
        StressCliParseResult.Help -> {
            println(StressCliParser.usage())
        }

        is StressCliParseResult.Run -> {
            val logger = LoggerFactory.getLogger("punishments.client.stress")
            logger.info(
                "Starting Punishment Service stress test with profile={} players={} spawnRate={} scenario={}s",
                parseResult.config.profile.cliName,
                parseResult.config.totalPlayers,
                parseResult.config.spawnRatePerSecond,
                parseResult.config.totalDurationSeconds
            )

            runBlocking {
                val artifacts = SimulationEngine(parseResult.config).run()
                logger.info("Stress test artifacts written to {}", artifacts.outputDirectory)
                logger.info(
                    "Artifacts: results={}, summaryJson={}, summaryCsv={}, report={}",
                    artifacts.resultsJsonl,
                    artifacts.summaryJson,
                    artifacts.summaryCsv,
                    artifacts.reportHtml
                )
            }
        }
    }
}

private fun parseOrExit(args: Array<String>): StressCliParseResult {
    return try {
        StressCliParser.parse(args)
    } catch (error: IllegalArgumentException) {
        System.err.println(error.message)
        System.err.println()
        System.err.println(StressCliParser.usage())
        exitProcess(1)
    }
}
