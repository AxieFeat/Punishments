package punishments.client.common.network

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.slf4j.LoggerFactory
import punishments.client.common.config.ClientConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BalancedGrpcChannel(private val config: ClientConfig) {

    private val logger = LoggerFactory.getLogger(BalancedGrpcChannel::class.java)
    private val channels = mutableListOf<ManagedChannel>()
    private val counter = AtomicInteger(0)

    fun connect() {
        for (address in config.serviceAddresses) {
            val parts = address.split(":")
            val host = parts[0]
            val port = parts.getOrElse(1) { "9090" }.toInt()

            repeat(config.channelsPerAddress) { index ->
                val channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .keepAliveTime(config.grpcKeepAliveSeconds, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .maxInboundMessageSize(16 * 1024 * 1024)
                    .maxInboundMetadataSize(16 * 1024)
                    .build()

                channels.add(channel)
                if (config.channelsPerAddress > 1) {
                    logger.info(
                        "gRPC channel {}/{} connected to {}:{}",
                        index + 1,
                        config.channelsPerAddress,
                        host,
                        port
                    )
                } else {
                    logger.info("gRPC channel connected to {}:{}", host, port)
                }
            }
        }

        logger.info(
            "Total gRPC channels: {} ({} addresses x {} per address)",
            channels.size,
            config.serviceAddresses.size,
            config.channelsPerAddress
        )
    }

    fun getChannel(): ManagedChannel {
        check(channels.isNotEmpty()) { "No gRPC channels available. Call connect() first." }
        val index = (counter.getAndIncrement() and Int.MAX_VALUE) % channels.size
        return channels[index]
    }

    fun shutdown() {
        channels.forEach { channel ->
            try {
                channel.shutdown().awaitTermination(10, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logger.warn("Error shutting down gRPC channel", e)
                channel.shutdownNow()
            }
        }
        channels.clear()
        logger.info("All gRPC channels shut down")
    }
}
