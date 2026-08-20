package com.devuloopers.knet.core.http.client

/**
 * Immutable trust material for a local HTTPS-inspecting proxy.
 *
 * The certificate is supplied as DER so portable HTTP code does not depend on filesystem paths,
 * JVM certificate types, or a concrete certificate engine. Platforms validate and consume a copy
 * only when constructing a proxy-configured client.
 *
 * @param certificateAuthorityDer DER-encoded certificate authority trusted to sign intercepted
 * server certificates. The input is defensively copied and must not be empty.
 * @throws IllegalArgumentException If [certificateAuthorityDer] is empty.
 */
class LocalProxyTlsTrust(certificateAuthorityDer: ByteArray) {
    private val certificateAuthorityDer = certificateAuthorityDer.copyOf()

    init {
        require(this.certificateAuthorityDer.isNotEmpty()) {
            "Local proxy certificate authority must not be empty."
        }
    }

    /** Returns a caller-owned copy for platform certificate parsing. */
    internal fun certificateAuthorityDerCopy(): ByteArray = certificateAuthorityDer.copyOf()
}
