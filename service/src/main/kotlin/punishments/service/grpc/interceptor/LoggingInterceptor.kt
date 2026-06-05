package punishments.service.grpc.interceptor

import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import org.slf4j.LoggerFactory

class LoggingInterceptor : ServerInterceptor {

    private val logger = LoggerFactory.getLogger(LoggingInterceptor::class.java)

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val methodName = call.methodDescriptor.fullMethodName
        val startTime = System.currentTimeMillis()

        val wrappedCall = object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            override fun close(status: Status, trailers: Metadata) {
                val duration = System.currentTimeMillis() - startTime
                if (status.isOk) {
                    logger.debug("gRPC call completed: {} in {}ms", methodName, duration)
                } else {
                    logger.warn(
                        "gRPC call failed: {} in {}ms - {}: {}",
                        methodName,
                        duration,
                        status.code,
                        status.description
                    )
                }
                super.close(status, trailers)
            }
        }

        return next.startCall(wrappedCall, headers)
    }
}
