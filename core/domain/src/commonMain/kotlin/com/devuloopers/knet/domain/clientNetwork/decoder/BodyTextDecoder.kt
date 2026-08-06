package com.devuloopers.knet.domain.clientNetwork.decoder

/**
 * Stage 4 decoder that translates [DecodedBodyResult] bytes into pure domain [DecodedTextResult] outcomes.
 */
public object BodyTextDecoder {

    /**
     * Translates a [DecodedBodyResult] into a domain [DecodedTextResult] using [BinaryDetector].
     *
     * @param result Decoded body result from [BodyDecoder].
     * @param headers List of HTTP header key-value pairs.
     * @param policy Configurable [BinaryDetectionPolicy].
     * @return [DecodedTextResult] containing pure domain outcome states.
     */
    public fun decode(
        result: DecodedBodyResult,
        headers: List<Pair<String, String>> = emptyList(),
        policy: BinaryDetectionPolicy = BinaryDetectionPolicy.DEFAULT
    ): DecodedTextResult {
        return when (result) {
            is DecodedBodyResult.Success -> decodeBytes(result.bytes, headers, result.encoding, policy)
            is DecodedBodyResult.Identity -> decodeBytes(result.bytes, headers, ContentEncoding.IDENTITY, policy)
            is DecodedBodyResult.UnsupportedEncoding -> {
                DecodedTextResult.UnsupportedEncoding(result.encoding, result.rawBytes.size.toLong())
            }
            is DecodedBodyResult.CorruptedEncoding -> {
                DecodedTextResult.DecodingError(result.encoding, result.errorMessage)
            }
        }
    }

    private fun decodeBytes(
        bytes: ByteArray,
        headers: List<Pair<String, String>>,
        encoding: ContentEncoding,
        policy: BinaryDetectionPolicy
    ): DecodedTextResult {
        if (bytes.isEmpty()) return DecodedTextResult.Success("", encoding)

        val contentType = headers.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second
        val binaryCategory = BinaryDetector.detectBinaryCategory(bytes, contentType, policy)

        if (binaryCategory != null) {
            return if (binaryCategory != BinaryCategory.GENERIC) {
                DecodedTextResult.BinaryKnownType(bytes.size.toLong(), contentType, binaryCategory)
            } else {
                DecodedTextResult.BinaryUnknownType(bytes.size.toLong(), contentType)
            }
        }

        return try {
            DecodedTextResult.Success(bytes.decodeToString(), encoding)
        } catch (_: Throwable) {
            DecodedTextResult.BinaryUnknownType(bytes.size.toLong(), contentType)
        }
    }
}
