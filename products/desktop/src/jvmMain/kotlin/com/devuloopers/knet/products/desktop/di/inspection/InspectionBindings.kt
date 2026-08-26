package com.devuloopers.knet.products.desktop.di.inspection

import com.devuloopers.knet.application.contract.inspection.*
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolExtension
import com.devuloopers.knet.application.coordinator.inspection.SemanticInspectionScheduler
import com.devuloopers.knet.application.usecase.inspection.ObserveInspectionAnnotationsUseCase
import com.devuloopers.knet.data.desktop.inspection.DesktopSemanticInspectionRuntime
import com.devuloopers.knet.data.desktop.inspection.RoomInspectionAnnotationAdapter
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLBreakpointExtension
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLDocumentParser
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLSemanticInspector
import com.devuloopers.knet.engine.sse.inspection.SseProtocolMessageDecoder
import com.devuloopers.knet.engine.sse.inspection.SseSemanticInspector
import com.devuloopers.knet.engine.sse.breakpoint.SseBreakpointExtension
import com.devuloopers.knet.engine.sse.protocol.SseLimits
import com.devuloopers.knet.engine.grpc.GrpcDescriptorRegistry
import com.devuloopers.knet.engine.grpc.GrpcProtocolMessageDecoder
import com.devuloopers.knet.engine.grpc.GrpcBreakpointExtension
import com.devuloopers.knet.engine.graphqlwebsocket.breakpoint.GraphQLWebSocketBreakpointExtension
import com.devuloopers.knet.engine.graphqlwebsocket.inspection.GraphQLWebSocketProtocolMessageDecoder
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.websocket.WebSocketBreakpointExtension
import com.devuloopers.knet.engine.websocket.WebSocketProtocolMessageDecoder
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadDecoder
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentationRegistry
import com.devuloopers.knet.storage.database.KNetDatabase
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Semantic inspectors, persisted annotations, capability truth, and bounded scheduling. */
internal val inspectionBindings: Module = module {
    single { SseLimits() }
    single { GrpcDescriptorRegistry() }
    single { GrpcProtocolMessageDecoder(get()) } bind ProtocolMessagePayloadDecoder::class
    single { GraphQLWebSocketEnvelopeParser(graphQLParser = get()) }
    single { GraphQLWebSocketProtocolMessageDecoder(get()) } bind ProtocolMessagePayloadDecoder::class
    single { WebSocketProtocolMessageDecoder() } bind ProtocolMessagePayloadDecoder::class
    single { SseProtocolMessageDecoder(get()) } bind ProtocolMessagePayloadDecoder::class
    single { ProtocolMessagePresentationRegistry(getAll<ProtocolMessagePayloadDecoder>()) }
    single { GraphQLDocumentParser() }
    single { GraphQLBreakpointExtension(get()) } bind BreakpointProtocolExtension::class
    single { GrpcBreakpointExtension() } bind BreakpointProtocolExtension::class
    single { WebSocketBreakpointExtension() } bind BreakpointProtocolExtension::class
    single { GraphQLWebSocketBreakpointExtension(get()) } bind BreakpointProtocolExtension::class
    single { SseBreakpointExtension(limits = get()) } bind BreakpointProtocolExtension::class
    single { GraphQLSemanticInspector(get()) } bind SemanticInspector::class
    single { SseSemanticInspector(get()) } bind SemanticInspector::class
    single<InspectionAnnotationStore> {
        RoomInspectionAnnotationAdapter(get<KNetDatabase>().canonicalCaptureDao())
    }
    single { SemanticInspectionScheduler(get(), get(), getAll()) }
    single { DesktopSemanticInspectionRuntime(get(), get(), get()) }
    single {
        RuntimeCapabilityCatalog(
            listOf(
                RuntimeCapability(
                    "http1-proxy",
                    "HTTP/1.0 and HTTP/1.1 proxy",
                    CapabilityMaturity.SUPPORTED,
                    "HttpOneZeroIntegrationTest and HttpOneStreamingSemanticsIntegrationTest"
                ),
                RuntimeCapability(
                    "graphql-inspector",
                    "GraphQL inspection",
                    CapabilityMaturity.SUPPORTED,
                    "SemanticInspectionEndToEndTest"
                ),
                RuntimeCapability(
                    "websocket.capture",
                    "WebSocket capture and inspection",
                    CapabilityMaturity.EXPERIMENTAL,
                    "WebSocketFrameCodecTest, WebSocketDuplexInspectorTest, and DuplexUpgradeIntegrationTest",
                ),
                RuntimeCapability(
                    "websocket.breakpoints",
                    "WebSocket message breakpoints",
                    CapabilityMaturity.EXPERIMENTAL,
                    "WebSocketBreakpointRuntimeTest",
                ),
                RuntimeCapability(
                    "websocket.apistudio",
                    "WebSocket API Studio",
                    CapabilityMaturity.EXPERIMENTAL,
                    "WebSocketApiStudioExecutorTest, WebSocketApiStudioAuthoringAdapterTest, and " +
                        "WebSocketWorkspaceDraftCodecTest",
                ),
                RuntimeCapability(
                    "graphql-websocket.capture",
                    "GraphQL WebSocket semantic inspection",
                    CapabilityMaturity.EXPERIMENTAL,
                    "GraphQLWebSocketProtocolTest and GraphQLWebSocketLayeringTest",
                ),
                RuntimeCapability(
                    "graphql-websocket.breakpoints",
                    "GraphQL WebSocket message breakpoints",
                    CapabilityMaturity.EXPERIMENTAL,
                    "GraphQLWebSocketLayeringTest and ProtocolMessageBreakpointRoutingTest",
                ),
                RuntimeCapability(
                    "graphql-websocket.apistudio",
                    "GraphQL subscription API Studio",
                    CapabilityMaturity.EXPERIMENTAL,
                    "GraphQLWebSocketApiStudioExecutorTest and GraphQLWebSocketWorkspaceDraftCodecTest",
                ),
                RuntimeCapability(
                    "sse.preview",
                    "Bounded Server-Sent Events semantic preview",
                    CapabilityMaturity.SUPPORTED,
                    "SseSemanticInspectionEndToEndTest and SseIncrementalParserTest",
                ),
                RuntimeCapability(
                    "sse.capture",
                    "Live Server-Sent Events capture",
                    CapabilityMaturity.EXPERIMENTAL,
                    "SseContentCodecsTest, SseStreamInspectorTest, and ProtocolLabIntegrationTest",
                ),
                RuntimeCapability(
                    "sse.apistudio",
                    "Live Server-Sent Events API Studio execution",
                    CapabilityMaturity.EXPERIMENTAL,
                    "ServerSentEventsStreamingTest, Http2TlsLabIntegrationTest, and " +
                        "SseHttpResponseStreamInterpreterTest",
                ),
                RuntimeCapability(
                    "sse.breakpoints",
                    "Server-Sent Events record breakpoints",
                    CapabilityMaturity.EXPERIMENTAL,
                    "SseBreakpointRuntimeTest and HttpTwoBreakpointIsolationTest",
                ),
                RuntimeCapability(
                    "http2",
                    "HTTP/2",
                    CapabilityMaturity.EXPERIMENTAL,
                    "HttpTwoDownstreamIntegrationTest, HttpTwoUpstreamIntegrationTest, " +
                        "HttpTwoBreakpointIsolationTest, and HttpTwoExecutionTest",
                ),
                RuntimeCapability(
                    "grpc.capture",
                    "gRPC capture and inspection",
                    CapabilityMaturity.EXPERIMENTAL,
                    "GrpcStreamInspectorTest, CanonicalMaintenanceTest, and ProtocolLabIntegrationTest",
                ),
                RuntimeCapability(
                    "grpc.breakpoints",
                    "gRPC message breakpoints",
                    CapabilityMaturity.EXPERIMENTAL,
                    "GrpcBreakpointRuntimeTest and BreakpointManagerViewModelTest",
                ),
                RuntimeCapability(
                    "grpc.apistudio",
                    "gRPC API Studio",
                    CapabilityMaturity.EXPERIMENTAL,
                    "GrpcApiStudioExecutorTest, GrpcApiStudioReflectionAdapterTest, and " +
                        "RoomApiStudioWorkspaceDocumentStoreTest",
                ),
                RuntimeCapability("http3", "HTTP/3", CapabilityMaturity.UNAVAILABLE),
                RuntimeCapability(
                    "manual-proxy",
                    "Manual proxy setup",
                    CapabilityMaturity.SUPPORTED,
                    "DesktopConnectivityArchitectureTest"
                ),
                RuntimeCapability(
                    "pac",
                    "PAC setup",
                    CapabilityMaturity.SUPPORTED,
                    "DesktopConnectivityArchitectureTest"
                ),
                RuntimeCapability(
                    "apple-profile",
                    "Apple profile setup",
                    CapabilityMaturity.SUPPORTED,
                    "DesktopConnectivityArchitectureTest"
                ),
                RuntimeCapability(
                    "adb-reverse",
                    "ADB reverse setup",
                    CapabilityMaturity.SUPPORTED,
                    "DesktopConnectivityArchitectureTest"
                ),
                RuntimeCapability(
                    "paired-proxy-gateway",
                    "Paired proxy gateway",
                    CapabilityMaturity.SUPPORTED,
                    "PairingGatewayEndToEndTest"
                ),
                RuntimeCapability("mobile-companion", "Mobile Companion app", CapabilityMaturity.UNAVAILABLE),
                RuntimeCapability("vpn", "VPN capture", CapabilityMaturity.UNAVAILABLE),
                RuntimeCapability("relay", "Remote relay", CapabilityMaturity.UNAVAILABLE),
            ),
        )
    }
    factory { ObserveInspectionAnnotationsUseCase(get()) }
}
