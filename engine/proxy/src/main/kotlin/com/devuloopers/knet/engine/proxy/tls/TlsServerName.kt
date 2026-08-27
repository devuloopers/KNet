package com.devuloopers.knet.engine.proxy.tls

/** Selects a bounded DNS identity from ClientHello SNI, falling back to the CONNECT authority. */
internal object TlsServerName {
    private const val MAX_HOST_LENGTH: Int = 253
    private const val MAX_LABEL_LENGTH: Int = 63

    /**
     * Returns the certificate identity for a TLS tunnel.
     *
     * A missing SNI value is valid for older clients and uses the already-validated CONNECT host.
     * A presented but malformed value is rejected instead of generating a certificate for
     * attacker-controlled non-host text.
     */
    fun select(clientHelloServerName: String?, connectHost: String): String {
        val presented = clientHelloServerName ?: return connectHost
        require(isValidDnsName(presented)) { "ClientHello contains an invalid SNI server name." }
        return presented.lowercase()
    }

    private fun isValidDnsName(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_HOST_LENGTH || value.any { it.code !in 0x21..0x7e }) {
            return false
        }
        return value.split('.').all(::isValidLabel)
    }

    private fun isValidLabel(label: String): Boolean {
        if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) return false
        if (!label.first().isAsciiLetterOrDigit() || !label.last().isAsciiLetterOrDigit()) return false
        return label.all { character -> character.isAsciiLetterOrDigit() || character == '-' }
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
