package punishments.client.stress.report

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import punishments.client.stress.metrics.RunSummary
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object StressReportWriter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun createOutputDirectory(baseDirectory: String, runId: String): Path {
        val path = Paths.get(baseDirectory).resolve(runId)
        Files.createDirectories(path)
        return path
    }

    fun writeSummary(outputDirectory: Path, summary: RunSummary) {
        writeString(outputDirectory.resolve("summary.json"), json.encodeToString(summary))
        writeString(outputDirectory.resolve("summary.csv"), buildCsv(summary))
        writeString(outputDirectory.resolve("report.html"), buildHtml(summary))
    }

    fun createRunId(): String {
        return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
    }

    private fun writeString(path: Path, content: String) {
        Files.writeString(path, content, CREATE, TRUNCATE_EXISTING, WRITE)
    }

    private fun buildCsv(summary: RunSummary): String {
        val lines = mutableListOf(
            "operation,total,ok,fail,retries,p50_ms,p95_ms,p99_ms,max_ms,error_codes"
        )
        summary.operations.forEach { operation ->
            val errorCodes = operation.errorCodes.entries.joinToString("|") { (code, count) -> "$code=$count" }
            lines += listOf(
                operation.operation,
                operation.total.toString(),
                operation.ok.toString(),
                operation.fail.toString(),
                operation.retries.toString(),
                operation.p50Ms.toString(),
                operation.p95Ms.toString(),
                operation.p99Ms.toString(),
                operation.maxMs.toString(),
                errorCodes
            ).joinToString(",")
        }
        return lines.joinToString(System.lineSeparator())
    }

    private fun buildHtml(summary: RunSummary): String {
        val phaseRows = summary.phases.joinToString(separator = "") { phase ->
            """
            <tr>
              <td>${escape(phase.phase)}</td>
              <td>${phase.durationSeconds}</td>
              <td>${phase.requestedStartPlayers}</td>
              <td>${phase.requestedEndPlayers}</td>
              <td>${phase.peakActivePlayers}</td>
              <td>${phase.peakSpawnRate}</td>
            </tr>
            """.trimIndent()
        }
        val operationRows = summary.operations.joinToString(separator = "") { operation ->
            """
            <tr>
              <td>${escape(operation.operation)}</td>
              <td>${operation.total}</td>
              <td>${operation.ok}</td>
              <td>${operation.fail}</td>
              <td>${operation.retries}</td>
              <td>${operation.p50Ms}</td>
              <td>${operation.p95Ms}</td>
              <td>${operation.p99Ms}</td>
              <td>${operation.maxMs}</td>
              <td>${escape(operation.errorCodes.entries.joinToString { (code, count) -> "$code=$count" })}</td>
            </tr>
            """.trimIndent()
        }
        val serverMetricRows = summary.serverMetrics.takeLast(MAX_SERVER_METRIC_ROWS).joinToString(separator = "") { snapshot ->
            """
            <tr>
              <td>${escape(snapshot.phase)}</td>
              <td>${"%.1f".format(snapshot.elapsedSeconds)}</td>
              <td>${escape(snapshot.url)}</td>
              <td>${snapshot.cacheHitRate.formatPercent()}</td>
              <td>${snapshot.l1HitRate.formatPercent()}</td>
              <td>${snapshot.l2HitRate.formatPercent()}</td>
              <td>${snapshot.cacheGeneration.formatNumber()}</td>
              <td>${snapshot.dbFallbacks.formatNumber()}</td>
              <td>${snapshot.redisErrors.formatNumber()}</td>
              <td>${snapshot.grpcCalls.formatNumber()}</td>
            </tr>
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <title>Punishment Service Stress Report</title>
              <style>
                body { font-family: Arial, sans-serif; margin: 24px; color: #1a1a1a; }
                table { border-collapse: collapse; width: 100%; margin-bottom: 24px; }
                th, td { border: 1px solid #d0d7de; padding: 8px 10px; text-align: left; }
                th { background: #f6f8fa; }
                h1, h2 { margin-bottom: 12px; }
                .meta { margin-bottom: 24px; }
                code { background: #f6f8fa; padding: 2px 4px; }
              </style>
            </head>
            <body>
              <h1>Punishment Service Stress Report</h1>
              <div class="meta">
                <p><strong>Run:</strong> <code>${escape(summary.runId)}</code></p>
                <p><strong>Started:</strong> ${escape(summary.startedAt)} | <strong>Finished:</strong> ${escape(summary.finishedAt)}</p>
                <p><strong>Duration:</strong> ${"%.2f".format(summary.durationSeconds)}s</p>
                <p><strong>Totals:</strong> ${summary.totals.operationCount} ops, ${summary.totals.okCount} ok, ${summary.totals.failCount} fail, ${summary.totals.retryCount} retries</p>
              </div>

              <h2>Scenario</h2>
              <table>
                <thead>
                  <tr>
                    <th>Phase</th>
                    <th>Duration (s)</th>
                    <th>Start Players</th>
                    <th>End Players</th>
                    <th>Peak Active Players</th>
                    <th>Peak Spawn Rate</th>
                  </tr>
                </thead>
                <tbody>$phaseRows</tbody>
              </table>

              <h2>Operations</h2>
              <table>
                <thead>
                  <tr>
                    <th>Operation</th>
                    <th>Total</th>
                    <th>OK</th>
                    <th>Fail</th>
                    <th>Retries</th>
                    <th>P50</th>
                    <th>P95</th>
                    <th>P99</th>
                    <th>Max</th>
                    <th>Error Codes</th>
                  </tr>
                </thead>
                <tbody>$operationRows</tbody>
              </table>

              <h2>Server Metrics</h2>
              <table>
                <thead>
                  <tr>
                    <th>Phase</th>
                    <th>Elapsed (s)</th>
                    <th>URL</th>
                    <th>Cache Hit Rate</th>
                    <th>L1 Hit Rate</th>
                    <th>L2 Hit Rate</th>
                    <th>Cache Generation</th>
                    <th>DB Fallbacks</th>
                    <th>Redis Errors</th>
                    <th>gRPC Requests</th>
                  </tr>
                </thead>
                <tbody>$serverMetricRows</tbody>
              </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun Double?.formatPercent(): String {
        return this?.let { "%.2f%%".format(it * 100.0) } ?: ""
    }

    private fun Double?.formatNumber(): String {
        return this?.let { "%.0f".format(it) } ?: ""
    }

    private fun escape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private const val MAX_SERVER_METRIC_ROWS = 100
}
