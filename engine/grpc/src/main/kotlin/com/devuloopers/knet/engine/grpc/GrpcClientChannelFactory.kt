package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolRoute
import io.grpc.HttpConnectProxiedSocketAddress
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress

/** Creates strict direct or KNet-proxied gRPC channels without exposing transport types outside the engine. */
class GrpcClientChannelFactory(
    localProxyRootCertificateDer: ByteArray,
    private val maximumInboundMessageBytes: Int = 16 * 1_024 * 1_024,
) {
    private val localRootCertificate: ByteArray = localProxyRootCertificateDer.copyOf()

    init {
        require(localRootCertificate.isNotEmpty()) { "Local proxy root certificate must not be empty." }
        require(maximumInboundMessageBytes > 0) { "Maximum inbound gRPC message size must be positive." }
    }

    fun create(
        host: String,
        port: Int,
        useTls: Boolean,
        route: ApiStudioProtocolRoute,
    ): ManagedChannel = NettyChannelBuilder.forAddress(host, port)
        .maxInboundMessageSize(maximumInboundMessageBytes)
        .apply {
            if (useTls) {
                if (route is ApiStudioProtocolRoute.LocalProxy) {
                    sslContext(
                        GrpcSslContexts.forClient()
                            .trustManager(ByteArrayInputStream(localRootCertificate))
                            .build(),
                    )
                } else {
                    useTransportSecurity()
                }
            } else {
                usePlaintext()
            }
            if (route is ApiStudioProtocolRoute.LocalProxy) {
                proxyDetector { targetAddress ->
                    HttpConnectProxiedSocketAddress.newBuilder()
                        .setProxyAddress(InetSocketAddress(route.host, route.port))
                        .setTargetAddress(targetAddress as InetSocketAddress)
                        .build()
                }
            }
        }
        .build()
}
