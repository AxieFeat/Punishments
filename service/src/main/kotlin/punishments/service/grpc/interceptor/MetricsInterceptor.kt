package punishments.service.grpc.interceptor

import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Low-cardinality gRPC metrics interceptor.
 *
 * Timers are cached by method/status so high-throughput calls do not allocate meter
 * builders on every request, while total requests remain available as one counter.
 */
class MetricsInterceptor(private val meterRegistry: MeterRegistry) : ServerInterceptor {

    private val timerCache = ConcurrentHashMap<String, Timer>()

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val methodName = call.methodDescriptor.bareMethodName ?: "unknown"
        val startNanos = System.nanoTime()

        meterRegistry.counter("grpc.server.requests").increment()
        meterRegistry.counter("grpc.server.calls", "method", methodName).increment()

        val wrappedCall = object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            override fun close(status: Status, trailers: Metadata) {
                val durationNanos = System.nanoTime() - startNanos
                val statusCode = status.code.name
                val timerKey = "$methodName:$statusCode"

                val timer = timerCache.computeIfAbsent(timerKey) {
                    Timer.builder("grpc.server.call.duration")
                        .description("gRPC call duration")
                        .tag("method", methodName)
                        .tag("status", statusCode)
                        .publishPercentileHistogram()
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .minimumExpectedValue(Duration.ofMillis(1))
                        .maximumExpectedValue(Duration.ofSeconds(30))
                        .register(meterRegistry)
                }
                timer.record(durationNanos, TimeUnit.NANOSECONDS)

                if (!status.isOk) {
                    meterRegistry.counter(
                        "grpc.server.calls.errors",
                        "method", methodName,
                        "status", statusCode
                    ).increment()
                }

                super.close(status, trailers)
            }
        }

        return next.startCall(wrappedCall, headers)
    }
}
