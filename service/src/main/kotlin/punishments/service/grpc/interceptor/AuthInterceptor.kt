package punishments.service.grpc.interceptor

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import org.slf4j.LoggerFactory

class AuthInterceptor(private val expectedToken: String) : ServerInterceptor {

    private val logger = LoggerFactory.getLogger(AuthInterceptor::class.java)

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        if (expectedToken.isBlank()) {
            return next.startCall(call, headers)
        }

        val token = headers.get(AUTH_KEY)
        if (token != expectedToken) {
            logger.warn("Unauthorized gRPC call: {}", call.methodDescriptor.fullMethodName)
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid or missing auth token"), Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }

        return next.startCall(call, headers)
    }

    private companion object {
        val AUTH_KEY: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
