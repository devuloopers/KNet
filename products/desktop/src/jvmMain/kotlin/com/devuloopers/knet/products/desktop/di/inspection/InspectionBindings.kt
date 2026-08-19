package com.devuloopers.knet.products.desktop.di.inspection

import com.devuloopers.knet.application.port.inspection.*
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolExtension
import com.devuloopers.knet.data.desktop.inspection.DesktopSemanticInspectionRuntime
import com.devuloopers.knet.data.desktop.inspection.RoomInspectionAnnotationAdapter
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLBreakpointExtension
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLDocumentParser
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLSemanticInspector
import com.devuloopers.knet.engine.protocol.inspector.sse.SseSemanticInspector
import com.devuloopers.knet.storage.database.KNetDatabase
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Semantic inspectors, persisted annotations, capability truth, and bounded scheduling. */
internal val inspectionBindings: Module = module {
    single { GraphQLDocumentParser() }
    single { GraphQLBreakpointExtension(get()) } bind BreakpointProtocolExtension::class
    single { GraphQLSemanticInspector(get()) } bind SemanticInspector::class
    single { SseSemanticInspector() } bind SemanticInspector::class
    single<InspectionAnnotationPort> {
        RoomInspectionAnnotationAdapter(get<KNetDatabase>().canonicalCaptureDao())
    }
    single { SemanticInspectionScheduler(get(), get(), getAll()) }
    single { DesktopSemanticInspectionRuntime(get(), get(), get()) }
    single {
        RuntimeCapabilityCatalog(
            listOf(
                RuntimeCapability(
                    "http1-proxy",
                    "HTTP/1.1 proxy",
                    CapabilityMaturity.SUPPORTED,
                    "HttpOneStreamingSemanticsIntegrationTest"
                ),
                RuntimeCapability(
                    "graphql-inspector",
                    "GraphQL inspection",
                    CapabilityMaturity.SUPPORTED,
                    "SemanticInspectionEndToEndTest"
                ),
                RuntimeCapability("websocket", "WebSocket frames", CapabilityMaturity.UNAVAILABLE),
                RuntimeCapability(
                    "sse",
                    "Server-Sent Events inspection",
                    CapabilityMaturity.SUPPORTED,
                    "SseSemanticInspectionEndToEndTest"
                ),
                RuntimeCapability("http2", "HTTP/2", CapabilityMaturity.UNAVAILABLE),
                RuntimeCapability("grpc", "gRPC", CapabilityMaturity.UNAVAILABLE),
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
