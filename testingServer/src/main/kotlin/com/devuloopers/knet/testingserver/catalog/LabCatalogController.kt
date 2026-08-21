package com.devuloopers.knet.testingserver.catalog

import com.devuloopers.knet.testingserver.grpc.GrpcServerLifecycle
import com.devuloopers.knet.testingserver.http2.Http2TlsLabServer
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Maturity exposed by the local lab without claiming an endpoint that does not exist. */
enum class LabCapabilityMaturity {
    AVAILABLE,
    PLANNED,
}

/** Transport or semantic protocol exercised by one lab capability. */
enum class LabProtocol {
    HTTP_1_1,
    HTTP_2_H2C,
    HTTP_2_TLS,
    GRAPHQL_HTTP,
    GRAPHQL_WEBSOCKET,
    SERVER_SENT_EVENTS,
    WEBSOCKET,
    GRPC,
    GRPC_WEB,
    HTTP_3,
    WEBTRANSPORT,
}

/**
 * One discoverable protocol-lab capability.
 *
 * @property id Stable machine-readable identifier.
 * @property protocol Protocol or transport exercised by the endpoint.
 * @property address Relative HTTP path or host-and-port listener description.
 * @property description Human-readable test purpose.
 * @property maturity Whether the endpoint is currently executable.
 */
data class LabCapability(
    val id: String,
    val protocol: LabProtocol,
    val address: String,
    val description: String,
    val maturity: LabCapabilityMaturity,
)

/**
 * Versioned discovery document for manual clients and automated integration tests.
 *
 * @property apiVersion Version of the test contract rather than the application release.
 * @property grpcPort Actual native gRPC listener port, including an ephemeral test port.
 * @property http2TlsPort Actual TLS/ALPN HTTP/2 listener port, including an ephemeral test port.
 * @property capabilities Supported and deliberately deferred protocol capabilities.
 */
data class LabManifest(
    val apiVersion: String,
    val grpcPort: Int,
    val http2TlsPort: Int,
    val capabilities: List<LabCapability>,
)

/** Publishes the canonical endpoint catalog for the local protocol lab. */
@RestController
@RequestMapping("/lab/v1")
class LabCatalogController(
    private val grpcServer: GrpcServerLifecycle,
    private val http2TlsServer: Http2TlsLabServer,
) {
    /**
     * Returns every executable fixture and the intentionally unavailable future transports.
     *
     * @return Stable discovery manifest for this protocol-lab contract.
     */
    @GetMapping
    fun manifest(): LabManifest = LabManifest(
        apiVersion = "1",
        grpcPort = grpcServer.boundPort,
        http2TlsPort = http2TlsServer.boundPort,
        capabilities = listOf(
            available("http", LabProtocol.HTTP_1_1, "/lab/v1/http/echo", "HTTP methods and metadata echo"),
            available("http2", LabProtocol.HTTP_2_H2C, "/lab/v1/http/echo", "HTTP/2 clear-text negotiation"),
            available(
                "http2-tls",
                LabProtocol.HTTP_2_TLS,
                "https://localhost:${http2TlsServer.boundPort}/lab/v1/http2/echo",
                "HTTP/2 TLS, ALPN, multiplexing, trailers, and frame faults",
            ),
            available("payloads", LabProtocol.HTTP_1_1, "/lab/v1/payload", "Structured and binary payload fixtures"),
            available("sse", LabProtocol.SERVER_SENT_EVENTS, "/lab/v1/streams/sse", "Bounded SSE stream"),
            available("websocket", LabProtocol.WEBSOCKET, "/lab/v1/websocket/echo", "Text and binary frame echo"),
            available("graphql", LabProtocol.GRAPHQL_HTTP, "/lab/v1/graphql", "Queries, mutations, and named operations"),
            available(
                "graphql-subscription",
                LabProtocol.GRAPHQL_WEBSOCKET,
                "/lab/v1/graphql/ws",
                "GraphQL subscription transport",
            ),
            available("grpc", LabProtocol.GRPC, "127.0.0.1:${grpcServer.boundPort}", "Native unary and streaming RPCs"),
            planned(
                "grpc-web",
                LabProtocol.GRPC_WEB,
                "HTTP adapter endpoint",
                "Requires a standards-compatible gRPC-Web adapter",
            ),
            planned("http3", LabProtocol.HTTP_3, "UDP/QUIC listener", "Requires a dedicated QUIC test profile"),
            planned(
                "webtransport",
                LabProtocol.WEBTRANSPORT,
                "HTTP/3 session listener",
                "Requires HTTP/3 capture and a WebTransport implementation",
            ),
        ),
    )

    private fun available(
        id: String,
        protocol: LabProtocol,
        address: String,
        description: String,
    ): LabCapability = LabCapability(id, protocol, address, description, LabCapabilityMaturity.AVAILABLE)

    private fun planned(
        id: String,
        protocol: LabProtocol,
        address: String,
        description: String,
    ): LabCapability = LabCapability(id, protocol, address, description, LabCapabilityMaturity.PLANNED)
}
