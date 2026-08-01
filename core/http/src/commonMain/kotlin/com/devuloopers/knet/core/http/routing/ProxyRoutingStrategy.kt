package com.devuloopers.knet.core.http.routing

import java.net.ConnectException
import java.net.SocketException

/**
 * Strategy interface governing HTTP client routing decisions and proxy failure fallbacks.
 */
interface ProxyRoutingStrategy {

    /**
     * Determines whether outbound requests should initially attempt proxy routing.
     *
     * @param proxyPort Optional proxy port integer.
     * @return `true` if proxy routing should be attempted; `false` for direct HTTP routing.
     */
    fun shouldAttemptProxy(proxyPort: Int?): Boolean

    /**
     * Evaluates whether a caught exception represents a local proxy connection failure
     * suitable for automatic fallback to direct HTTP execution.
     *
     * @param exception Caught execution exception.
     * @param proxyPort Proxy port targeted by the failed call.
     * @return `true` if fallback should be triggered; `false` otherwise.
     */
    fun isProxyConnectionFailure(exception: Throwable, proxyPort: Int?): Boolean
}

/**
 * Default implementation of [ProxyRoutingStrategy].
 *
 * Directs traffic through proxy if proxyPort is non-null, and triggers automatic fallback
 * to direct execution when socket connection errors ([ConnectException], [SocketException])
 * occur on the proxy endpoint.
 */
class DefaultProxyRoutingStrategy : ProxyRoutingStrategy {

    override fun shouldAttemptProxy(proxyPort: Int?): Boolean {
        return proxyPort != null && proxyPort > 0
    }

    override fun isProxyConnectionFailure(exception: Throwable, proxyPort: Int?): Boolean {
        if (proxyPort == null) return false

        var current: Throwable? = exception
        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            if (current is ConnectException ||
                current is SocketException ||
                message.contains("connection refused") ||
                message.contains("failed to connect") ||
                message.contains("connection reset")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
