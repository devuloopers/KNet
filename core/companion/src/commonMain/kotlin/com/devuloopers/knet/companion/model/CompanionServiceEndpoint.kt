package com.devuloopers.knet.companion.model

/** Application-layer scheme used to reach one companion service endpoint. */
public enum class CompanionEndpointScheme {
    HTTP,
    HTTPS,
}

/** Reachable companion service endpoint; brackets are not stored around IPv6 host text. */
public data class CompanionServiceEndpoint(
    public val host: String,
    public val port: Int,
    public val scheme: CompanionEndpointScheme,
) {
    init {
        require(host.length in 1..255 && host == host.trim() && host.none(Char::isUnsafeEndpointCharacter)) {
            "Companion endpoint host is invalid."
        }
        require(port in 1..65_535) { "Companion endpoint port must be between 1 and 65535." }
    }
}

private fun Char.isUnsafeEndpointCharacter(): Boolean =
    code in 0..31 || code == 127 || isWhitespace() || this in "/\\?#@[]%"
