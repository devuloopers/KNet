package com.devuloopers.knet.testingserver.http2

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

/**
 * Configuration for the independent TLS/ALPN HTTP/2 protocol-lab listener.
 *
 * @property host Interface on which the lab listener is bound.
 * @property port Requested TCP port. Zero requests an operating-system-selected ephemeral port.
 */
@ConfigurationProperties("knet.testing-server.http2-tls")
data class Http2TlsLabProperties @ConstructorBinding constructor(
    val host: String,
    val port: Int,
) {
    init {
        require(host.isNotBlank()) { "HTTP/2 TLS test host must not be blank." }
        require(port in MIN_PORT..MAX_PORT) { "HTTP/2 TLS test port must be between 0 and 65535." }
    }

    private companion object {
        const val MIN_PORT = 0
        const val MAX_PORT = 65_535
    }
}
