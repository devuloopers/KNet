package com.devuloopers.knet.engine.proxy.upstream

import com.devuloopers.knet.engine.proxy.tls.TlsServerName
import io.netty.util.NetUtil

/** Strongly typed result explaining which identity is used for upstream DNS routing. */
internal data class UpstreamRouteHostSelection(
    val primaryHost: String,
    val fallbackDnsHost: String?,
)

/**
 * Selects the routable hostname after an intercepted TLS ClientHello has exposed SNI.
 *
 * VPNs and some explicit-proxy clients send CONNECT with an already-resolved IP address. If that
 * address is an unreachable IPv6 candidate, using only it prevents KNet from discovering the
 * hostname's usable IPv4 addresses. A validated DNS SNI identity supplies an ordered fallback;
 * the exact CONNECT destination remains first and therefore retains transparent VPN semantics.
 */
internal object UpstreamRouteHostSelector {
    fun select(
        connectHost: String,
        tlsServerName: String?,
        isTls: Boolean,
    ): UpstreamRouteHostSelection {
        val dnsTlsServerName = tlsServerName
            ?.takeIf(String::isNotBlank)
            ?.takeUnless(::isIpLiteral)
        val fallbackDnsHost = dnsTlsServerName.takeIf { isTls && isIpLiteral(connectHost) }
        return UpstreamRouteHostSelection(
            primaryHost = connectHost,
            fallbackDnsHost = fallbackDnsHost,
        )
    }

    /** Keeps the IPv6 CONNECT target first and adds DNS candidates only for a valid ClientHello name. */
    fun selectClassifiedTunnel(connectHost: String, tlsServerName: String?): UpstreamRouteHostSelection {
        val fallback = tlsServerName
            ?.takeIf { NetUtil.isValidIpV6Address(connectHost) }
            ?.let { name -> runCatching { TlsServerName.select(name, connectHost) }.getOrNull() }
        return select(connectHost, fallback, isTls = true)
    }

    private fun isIpLiteral(value: String): Boolean =
        NetUtil.isValidIpV4Address(value) || NetUtil.isValidIpV6Address(value)
}
