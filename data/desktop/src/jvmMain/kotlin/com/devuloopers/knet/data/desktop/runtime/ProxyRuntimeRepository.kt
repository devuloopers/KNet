package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer

/**
 * Desktop runtime coordinator managing Netty proxy server lifecycle.
 */
class ProxyRuntimeRepository(
    private val certificateAuthority: CertificateAuthority,
    private val certificateCache: CertificateCache
) {
    private var proxyServer: KNetProxyServer? = null

    /**
     * Starts the Netty MITM proxy server on the specified port.
     */
    fun startProxy(port: Int = 8080, trafficListener: ProxyTrafficListener) {
        if (proxyServer != null) {
            KNetLogger.warn(tag = "ProxyRuntimeRepository") { "Proxy server is already running." }
            return
        }
        KNetLogger.info(tag = "ProxyRuntimeRepository") { "Starting Netty proxy server on port $port..." }
        val server = KNetProxyServer(
            port = port,
            ca = certificateAuthority,
            certCache = certificateCache,
            listener = trafficListener
        )
        server.start()
        proxyServer = server
    }

    /**
     * Stops the running proxy server.
     */
    fun stopProxy() {
        proxyServer?.let { server ->
            KNetLogger.info(tag = "ProxyRuntimeRepository") { "Stopping Netty proxy server..." }
            server.stop()
            proxyServer = null
        }
    }

    /**
     * Returns whether the proxy server is currently active.
     */
    fun isRunning(): Boolean = proxyServer != null
}
