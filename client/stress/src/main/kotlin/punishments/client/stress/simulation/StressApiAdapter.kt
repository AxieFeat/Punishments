package punishments.client.stress.simulation

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.delay
import punishments.client.common.network.GrpcPunishmentClient
import punishments.common.error.PunishmentException
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

class StressApiAdapter(
    private val client: GrpcPunishmentClient,
    private val retryAttempts: Int
) : AutoCloseable {

    suspend fun <T> execute(block: suspend GrpcPunishmentClient.() -> T): ApiExecution<T> {
        val startedAt = System.nanoTime()
        var retries = 0
        var lastFailure: ApiExecution.Failure? = null

        repeat(retryAttempts) { attemptIndex ->
            try {
                return ApiExecution.Success(
                    value = client.block(),
                    retries = retries,
                    latencyMs = elapsedMs(startedAt)
                )
            } catch (throwable: Throwable) {
                val errorCode = throwable.toMetricErrorCode()
                val retryable = throwable.isRetryableGrpcFailure()
                val isLastAttempt = attemptIndex == retryAttempts - 1
                lastFailure = ApiExecution.Failure(
                    retries = retries,
                    latencyMs = elapsedMs(startedAt),
                    throwable = throwable,
                    errorCode = errorCode
                )
                if (!retryable || isLastAttempt) {
                    return lastFailure
                }

                retries++
                delay(backoffMs(retries).milliseconds)
            }
        }

        return lastFailure ?: ApiExecution.Failure(
            retries = retries,
            latencyMs = elapsedMs(startedAt),
            throwable = IllegalStateException("Retry loop exited unexpectedly"),
            errorCode = "UNKNOWN"
        )
    }

    override fun close() {
        client.close()
    }

    private fun elapsedMs(startedAt: Long): Long {
        return (System.nanoTime() - startedAt) / 1_000_000
    }

    private fun backoffMs(retries: Int): Long {
        return min(100L * (1L shl retries.coerceAtMost(4)), 2_000L)
    }

    private fun Throwable.isRetryableGrpcFailure(): Boolean {
        val code = when (this) {
            is StatusException -> status.code
            is StatusRuntimeException -> status.code
            else -> null
        }
        return code in RETRYABLE_CODES
    }

    private fun Throwable.toMetricErrorCode(): String {
        return when (this) {
            is StatusException -> status.code.name
            is StatusRuntimeException -> status.code.name
            is PunishmentException -> errorCode.name
            else -> javaClass.simpleName.ifBlank { "UNKNOWN" }
        }
    }

    private companion object {
        val RETRYABLE_CODES = setOf(
            Status.Code.UNAVAILABLE,
            Status.Code.DEADLINE_EXCEEDED,
            Status.Code.ABORTED,
            Status.Code.UNKNOWN,
            Status.Code.CANCELLED
        )
    }
}

sealed interface ApiExecution<out T> {
    data class Success<T>(
        val value: T,
        val retries: Int,
        val latencyMs: Long
    ) : ApiExecution<T>

    data class Failure(
        val retries: Int,
        val latencyMs: Long,
        val throwable: Throwable,
        val errorCode: String
    ) : ApiExecution<Nothing>
}
