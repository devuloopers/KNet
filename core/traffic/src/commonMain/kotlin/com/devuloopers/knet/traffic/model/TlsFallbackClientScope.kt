package com.devuloopers.knet.traffic.model

/** Client boundary for temporary TLS decisions, derived solely from admitted transport metadata. */
public sealed interface TlsFallbackClientScope {
    /** Authorized ingress identity; the original bridge socket does not identify the device.
     * @property kind Trusted ingress category.
     * @property identity Authorized device/client identity. */
    public data class Identified(public val kind: IngressKind, public val identity: ClientIdentity) : TlsFallbackClientScope
    /** Local desktop clients without application attribution share one explicit local scope. */
    public data object LocalDesktop : TlsFallbackClientScope
    /** Unauthenticated clients are isolated by admitted numeric address and ingress.
     * @property kind Trusted ingress category.
     * @property address Admitted numeric source address without its ephemeral port. */
    public data class NetworkAddress(public val kind: IngressKind, public val address: String) : TlsFallbackClientScope
}

/**
 * Derives a scope from trusted admission context, never CONNECT headers or unverified application strings.
 * Unknown loopback bridges cannot be learned because they may represent unrelated devices.
 * @param ingress Admission attribution supplied by the proxy or authorized bridge.
 * @param downstreamHost Actual numeric downstream address without its ephemeral port.
 * @return A bounded client scope, or null when client isolation cannot be established.
 */
public fun tlsFallbackClientScope(ingress: IngressContext, downstreamHost: String?): TlsFallbackClientScope? {
    ingress.clientIdentity?.let { return TlsFallbackClientScope.Identified(ingress.kind, it) }
    val address = downstreamHost?.lowercase()?.takeIf { it.length in 2..255 && it.all { c -> c in "0123456789abcdef:." } } ?: return null
    if ('.' !in address && ':' !in address) return null
    val loopback = address == "::1" || address == "0:0:0:0:0:0:0:1" || address.startsWith("127.")
    if (loopback) return TlsFallbackClientScope.LocalDesktop.takeIf { ingress.kind == IngressKind.Local }
    return TlsFallbackClientScope.NetworkAddress(ingress.kind, address)
}
