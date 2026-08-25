package com.devuloopers.knet.products.desktop.di.proxy

import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.traffic.CaptureSessionControlPort
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.proxy.StartLoopbackProxyUseCase
import com.devuloopers.knet.application.usecase.proxy.StopProxyRuntimeUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureSessionFactory
import com.devuloopers.knet.data.desktop.proxy.repository.DesktopProxyRuntimeAdapter
import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.data.desktop.runtime.ProxyRuntimeRepository
import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.traffic.model.IngressAttributionLookup
import com.devuloopers.knet.engine.grpc.GrpcStreamInspectorFactory
import com.devuloopers.knet.engine.grpc.GrpcMessageBreakpointTransformerFactory
import com.devuloopers.knet.engine.websocket.WebSocketBreakpointTransformerFactory
import com.devuloopers.knet.engine.websocket.WebSocketDuplexInspectorFactory
import com.devuloopers.knet.engine.websocket.WebSocketSemanticBreakpointLayer
import com.devuloopers.knet.engine.graphqlwebsocket.breakpoint.GraphQLWebSocketBreakpointLayer
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageBreakpointGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Proxy transport runtime, breakpoint gate, capture session, and proxy-control composition. */
internal val proxyBindings: Module = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { GraphQLWebSocketBreakpointLayer(get()) } bind WebSocketSemanticBreakpointLayer::class
    single {
        GrpcMessageBreakpointTransformerFactory(
            gate = get<ProtocolMessageBreakpointGate>(),
            scope = get(),
        )
    }
    single {
        WebSocketBreakpointTransformerFactory(
            gate = get<ProtocolMessageBreakpointGate>(),
            scope = get(),
            semanticLayers = getAll<WebSocketSemanticBreakpointLayer>(),
        )
    }
    single {
        val certificates: CertificateRuntimeRepository = get()
        val certificateManager: CertificateManager = get()
        ProxyRuntimeRepository(
            serverTlsContextProvider = certificates.serverTlsContextProvider(),
            keyManagerProvider = certificateManager::getKeyManagerFactory,
            breakpointGate = get(),
            ingressAttribution = get<IngressAttributionLookup>(),
            streamInspectorFactories = listOf(GrpcStreamInspectorFactory()),
            streamTransformerFactories = listOf(get<GrpcMessageBreakpointTransformerFactory>()),
            duplexInspectorFactories = listOf(WebSocketDuplexInspectorFactory()),
            duplexTransformerFactories = listOf(get<WebSocketBreakpointTransformerFactory>()),
        )
    }
    single {
        CanonicalCaptureSessionFactory(
            database = get(),
            bodyStore = get(),
            bodyStoreMaintenance = get(),
        )
    }
    single {
        DesktopProxyRuntimeAdapter(
            proxyRuntimeRepository = get(),
            canonicalCaptureSessionFactory = get(),
            breakpointCaptureAvailability = get(),
        )
    }
    single<ProxyRuntimePort> { get<DesktopProxyRuntimeAdapter>() }
    single<CaptureSessionControlPort> { get<DesktopProxyRuntimeAdapter>() }
    factory { StartLoopbackProxyUseCase(get()) }
    factory { StopProxyRuntimeUseCase(get()) }
    factory { ObserveProxyRuntimeStateUseCase(get()) }
    factory { ObserveTrafficCaptureStateUseCase(get()) }
}
