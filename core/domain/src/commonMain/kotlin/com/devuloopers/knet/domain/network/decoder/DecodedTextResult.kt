package com.devuloopers.knet.domain.network.decoder

/**
 * Pure domain sealed interface representing the text/binary inspection outcome of an HTTP body payload.
 * Contains zero presentation or UI strings.
 */
public sealed interface DecodedTextResult {
    /**
     * Successfully decoded human-readable text.
     *
     * @property text The decoded string content.
     * @property encoding The [ContentEncoding] enum method used to decompress the body.
     */
    public data class Success(
        val text: String,
        val encoding: ContentEncoding = ContentEncoding.IDENTITY
    ) : DecodedTextResult

    /**
     * Binary payload with a recognized media type category (e.g. OHTTP, ProtoBuf, CBOR, WASM, Image).
     *
     * @property size Size of the byte payload.
     * @property mediaType HTTP Content-Type header value, or null.
     * @property category The [BinaryCategory] classification.
     */
    public data class BinaryKnownType(
        val size: Long,
        val mediaType: String?,
        val category: BinaryCategory
    ) : DecodedTextResult

    /**
     * Binary payload with an unknown or generic media type (fallback to Hex viewer).
     *
     * @property size Size of the byte payload.
     * @property mediaType HTTP Content-Type header value, or null.
     */
    public data class BinaryUnknownType(
        val size: Long,
        val mediaType: String?
    ) : DecodedTextResult

    /**
     * Decompression failure state.
     *
     * @property encoding The Content-Encoding method attempted.
     * @property errorMessage Descriptive exception message.
     */
    public data class DecodingError(
        val encoding: String,
        val errorMessage: String
    ) : DecodedTextResult

    /**
     * Unsupported Content-Encoding header state (e.g. "custom-alg").
     *
     * @property encoding The unsupported encoding string.
     * @property size Size of the raw encoded byte payload.
     */
    public data class UnsupportedEncoding(
        val encoding: String,
        val size: Long
    ) : DecodedTextResult
}
