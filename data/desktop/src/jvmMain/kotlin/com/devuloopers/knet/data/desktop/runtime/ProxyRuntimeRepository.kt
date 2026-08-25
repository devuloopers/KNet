package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import com.devuloopers.knet.application.port.breakpoint.BreakpointGate
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.interceptor.KNetInterceptorHandler
import com.devuloopers.knet.engine.interceptor.KNetBreakpointRequestAggregator
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import com.devuloopers.knet.data.desktop.certificate.DesktopServerTlsContextProvider
import com.devuloopers.knet.traffic.model.IngressAttributionLookup
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamInspectorFactory
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformerFactory

/**
 * Desktop runtime coordinator managing Netty proxy server lifecycle.
 */
class ProxyRuntimeRepository(
    private val serverTlsContextProvider: ServerTlsContextProvider,
    private val keyManagerProvider: KeyManagerProvider? = null,
    private val breakpointGate: BreakpointGate,
    private val ingressAttribution: IngressAttributionLookup? = null,
    private val streamInspectorFactories: List<ProxyStreamInspectorFactory> = emptyList(),
    private val streamTransformerFactories: List<ProxyStreamTransformerFactory> = emptyList(),
) {
    constructor(
        certificateAuthority: CertificateAuthority,
        certificateCache: CertificateCache,
        keyManagerProvider: KeyManagerProvider? = null,
        breakpointGate: BreakpointGate,
        ingressAttribution: IngressAttributionLookup? = null,
        streamInspectorFactories: List<ProxyStreamInspectorFactory> = emptyList(),
        streamTransformerFactories: List<ProxyStreamTransformerFactory> = emptyList(),
    ) : this(
        serverTlsContextProvider = DesktopServerTlsContextProvider(certificateAuthority, certificateCache),
        keyManagerProvider = keyManagerProvider,
        breakpointGate = breakpointGate,
        ingressAttribution = ingressAttribution,
        streamInspectorFactories = streamInspectorFactories,
        streamTransformerFactories = streamTransformerFactories,
    )

    private val lifecycleLock = Any()
    private var proxyServer: KNetProxyServer? = null
    private var closed = false

    /**
     * Starts the Netty MITM proxy server using an explicit listener and upstream trust policy.
     *
     * @param port TCP port to bind.
     * @param host Explicit listener host; loopback is the safe default.
     * @param verifyUpstreamTls Whether upstream certificates must be verified.
     * @param runtimePolicy Concrete timeout and connection limits enforced by Netty.
     * @param captureSink Canonical non-blocking streaming capture side output.
     * @throws Exception When listener startup fails after the server rolls back allocated resources.
     */
    fun startProxy(
        port: Int = 8080,
        host: String = KNetProxyServer.DEFAULT_BIND_HOST,
        verifyUpstreamTls: Boolean = true,
        runtimePolicy: KNetProxyRuntimePolicy = KNetProxyRuntimePolicy(),
        captureSink: ProxyCaptureSink,
    ) {
        synchronized(lifecycleLock) {
            check(!closed) { "Proxy runtime repository is closed." }
            if (proxyServer != null) {
                KNetLogger.warn(tag = LogTags.PROXY) { "Proxy server is already running." }
                return
            }
            KNetLogger.info(tag = LogTags.PROXY) { "Starting Netty proxy server on port $port..." }

            val pipelineInitializers = listOf<(io.netty.channel.ChannelPipeline) -> Unit>({ pipeline ->
                pipeline.addLast(
                    PipelineHandlerNames.SELECTIVE_HTTP_AGGREGATOR,
                    KNetBreakpointRequestAggregator(breakpointGate),
                )
                pipeline.addLast("knetInterceptorHandler", KNetInterceptorHandler(breakpointGate))
            })
            val server = KNetProxyServer(
                bindHost = host,
                port = port,
                serverTlsContextProvider = serverTlsContextProvider,
                keyManagerProvider = keyManagerProvider,
                verifyUpstreamTls = verifyUpstreamTls,
                runtimePolicy = runtimePolicy,
                pipelineInitializers = pipelineInitializers,
                captureSink = captureSink,
                ingressAttribution = ingressAttribution,
                requiresFullResponseAggregation = { request ->
                    breakpointGate.mayIntercept(request, BreakpointPhase.RESPONSE)
                },
                streamInspectorFactories = streamInspectorFactories,
                streamTransformerFactories = streamTransformerFactories,
            )
            server.start()
            proxyServer = server
        }
    }

    /**
     * Stops the running proxy server.
     */
    fun stopProxy() {
        val server = synchronized(lifecycleLock) {
            proxyServer.also { proxyServer = null }
        }
        server?.let {
            KNetLogger.info(tag = LogTags.PROXY) { "Stopping Netty proxy server..." }
            it.stop()
        }
    }

    /**
     * Returns whether the proxy server is currently active.
     */
    fun isRunning(): Boolean = synchronized(lifecycleLock) { proxyServer != null }

    /** Permanently closes this repository and any active proxy server. */
    fun close() {
        val shouldClose = synchronized(lifecycleLock) {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (!shouldClose) return
        stopProxy()
    }
}
