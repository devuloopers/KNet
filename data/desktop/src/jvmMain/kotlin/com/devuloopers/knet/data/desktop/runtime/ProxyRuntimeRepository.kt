package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.interceptor.KNetInterceptorHandler
import com.devuloopers.knet.engine.portal.MobilePortalHandler
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider

/**
 * Desktop runtime coordinator managing Netty proxy server lifecycle.
 */
class ProxyRuntimeRepository(
    private val certificateAuthority: CertificateAuthority,
    private val certificateCache: CertificateCache,
    private val keyManagerProvider: KeyManagerProvider? = null
) {
    private var proxyServer: KNetProxyServer? = null

    /**
     * Starts the Netty MITM proxy server on the specified port.
     */
    fun startProxy(port: Int = 8080, trafficListener: ProxyTrafficListener) {
        if (proxyServer != null) {
            KNetLogger.warn(tag = LogTags.PROXY) { "Proxy server is already running." }
            return
        }
        KNetLogger.info(tag = LogTags.PROXY) { "Starting Netty proxy server on port $port..." }
        
        // Ensure MobilePortalHandler and KNetInterceptorHandler are registered after httpAggregator in Netty pipeline
        KNetProxyServer.pipelineInitializers.clear()
        KNetProxyServer.pipelineInitializers.add { pipeline ->
            pipeline.addLast("mobilePortalHandler", MobilePortalHandler(ca = certificateAuthority, proxyPort = port))
            pipeline.addLast("knetInterceptorHandler", KNetInterceptorHandler(trafficListener))
        }
        val server = KNetProxyServer(
            port = port,
            ca = certificateAuthority,
            certCache = certificateCache,
            listener = trafficListener,
            keyManagerProvider = keyManagerProvider
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
