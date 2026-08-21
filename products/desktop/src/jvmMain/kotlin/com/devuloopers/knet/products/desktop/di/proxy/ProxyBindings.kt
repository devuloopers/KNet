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
import org.koin.core.module.Module
import org.koin.dsl.module

/** Proxy transport runtime, breakpoint gate, capture session, and proxy-control composition. */
internal val proxyBindings: Module = module {
    single {
        val certificates: CertificateRuntimeRepository = get()
        val certificateManager: CertificateManager = get()
        ProxyRuntimeRepository(
            serverTlsContextProvider = certificates.serverTlsContextProvider(),
            keyManagerProvider = certificateManager::getKeyManagerFactory,
            breakpointGate = get(),
            ingressAttribution = get<IngressAttributionLookup>(),
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
