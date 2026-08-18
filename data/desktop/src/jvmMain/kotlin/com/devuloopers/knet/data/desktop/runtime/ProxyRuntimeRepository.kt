package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import com.devuloopers.knet.application.port.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.port.breakpoint.BreakpointGate
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.interceptor.KNetInterceptorHandler
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider
import com.devuloopers.knet.traffic.model.IngressAttributionLookup

/**
 * Desktop runtime coordinator managing Netty proxy server lifecycle.
 */
class ProxyRuntimeRepository(
    private val certificateAuthority: CertificateAuthority,
    private val certificateCache: CertificateCache,
    private val keyManagerProvider: KeyManagerProvider? = null,
    private val breakpointGate: BreakpointGate = BreakpointCoordinator(),
    private val ingressAttribution: IngressAttributionLookup? = null,
) {
    private var proxyServer: KNetProxyServer? = null

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
        if (proxyServer != null) {
            KNetLogger.warn(tag = LogTags.PROXY) { "Proxy server is already running." }
            return
        }
        KNetLogger.info(tag = LogTags.PROXY) { "Starting Netty proxy server on port $port..." }
        
        val pipelineInitializers = listOf<(io.netty.channel.ChannelPipeline) -> Unit>({ pipeline ->
            pipeline.addLast("knetInterceptorHandler", KNetInterceptorHandler(breakpointGate))
        })
        val server = KNetProxyServer(
            bindHost = host,
            port = port,
            ca = certificateAuthority,
            certCache = certificateCache,
            keyManagerProvider = keyManagerProvider,
            verifyUpstreamTls = verifyUpstreamTls,
            runtimePolicy = runtimePolicy,
            pipelineInitializers = pipelineInitializers,
            captureSink = captureSink,
            ingressAttribution = ingressAttribution,
            requiresFullResponseAggregation = {
                breakpointGate.requirements.value.hasResponseRules
            },
            requiresFullRequestAggregation = {
                breakpointGate.requirements.value.hasRequestRules ||
                    breakpointGate.requirements.value.hasResponseRules
            },
        )
        server.start()
        proxyServer = server
    }

    /**
     * Flushes active client connection channels.
     */
    fun flushActiveChannels() {
        proxyServer?.flushActiveChannels()
    }

    /**
     * Stops the running proxy server.
     */
    fun stopProxy() {
        proxyServer?.let { server ->
            KNetLogger.info(tag = LogTags.PROXY) { "Stopping Netty proxy server..." }
            server.stop()
            proxyServer = null
        }
    }

    /**
     * Returns whether the proxy server is currently active.
     */
    fun isRunning(): Boolean = proxyServer != null
}
