package com.devuloopers.knet.domain.clientNetwork.decoder

/**
 * Platform-independent expect object for decoding HTTP Content-Encoding transport layers.
 */
expect object BodyDecoder {

    /**
     * Decodes the HTTP body [body] based on the `Content-Encoding` header found in [headers].
     *
     * @param body Raw network byte array, or null.
     * @param headers List of HTTP header key-value pairs.
     * @param maximumOutputBytes Maximum decoded byte count across every encoding layer.
     * @return [DecodedBodyResult] representing success, identity passthrough, unsupported encoding, or corruption.
     */
    fun decode(
        body: ByteArray?,
        headers: List<Pair<String, String>>,
        maximumOutputBytes: Int = 16 * 1024 * 1024,
    ): DecodedBodyResult
}
