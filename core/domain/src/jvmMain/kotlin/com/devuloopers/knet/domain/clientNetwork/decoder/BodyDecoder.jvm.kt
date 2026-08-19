package com.devuloopers.knet.domain.clientNetwork.decoder

import com.devuloopers.knet.traffic.model.body.ContentEncoding

/**
 * JVM implementation of [BodyDecoder] using composable SPI [ContentDecoder] strategy implementations.
 */
actual object BodyDecoder {

    private val decoders: Map<ContentEncoding, ContentDecoder> = listOf(
        GzipContentDecoder(),
        DeflateContentDecoder(),
        BrotliContentDecoder(),
        ZstdContentDecoder()
    ).associateBy { it.encoding }

    actual fun decode(
        body: ByteArray?,
        headers: List<Pair<String, String>>,
        maximumOutputBytes: Int,
    ): DecodedBodyResult {
        require(maximumOutputBytes in 1..MAXIMUM_DECODED_BYTES) {
            "Decoded output limit must be between 1 and $MAXIMUM_DECODED_BYTES bytes."
        }
        if (body == null || body.isEmpty()) {
            return DecodedBodyResult.Identity(ByteArray(0))
        }

        val encodingHeader = headers.firstOrNull { it.first.equals("Content-Encoding", ignoreCase = true) }?.second?.trim()?.lowercase()
            ?: return identity(body, maximumOutputBytes)

        if (encodingHeader.isBlank() || encodingHeader == "identity") {
            return identity(body, maximumOutputBytes)
        }

        val encodingChain = encodingHeader.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("identity", ignoreCase = true) }

        if (encodingChain.isEmpty()) {
            return identity(body, maximumOutputBytes)
        }

        var currentBytes: ByteArray = body
        var lastResolvedEncoding: ContentEncoding = ContentEncoding.IDENTITY

        // Process encoding chain in reverse order (right-to-left) as specified by HTTP RFC 7231
        for (token in encodingChain.reversed()) {
            val enumEncoding = ContentEncoding.fromToken(token)
            if (enumEncoding is ContentEncoding.Custom) {
                return DecodedBodyResult.UnsupportedEncoding(token, body)
            }

            val decoder = decoders[enumEncoding]
                ?: return DecodedBodyResult.UnsupportedEncoding(token, body)

            currentBytes = try {
                decoder.decompress(currentBytes, maximumOutputBytes)
            } catch (_: DecodedOutputLimitException) {
                return DecodedBodyResult.OutputLimitExceeded(token, maximumOutputBytes)
            } catch (exception: Exception) {
                return DecodedBodyResult.CorruptedEncoding(
                    token,
                    exception.message ?: "Decompression failed for $token",
                    body,
                )
            }

            lastResolvedEncoding = enumEncoding
        }

        return DecodedBodyResult.Success(currentBytes, lastResolvedEncoding)
    }

    private fun identity(body: ByteArray, maximumOutputBytes: Int): DecodedBodyResult =
        if (body.size <= maximumOutputBytes) {
            DecodedBodyResult.Identity(body)
        } else {
            DecodedBodyResult.OutputLimitExceeded(ContentEncoding.IDENTITY.token, maximumOutputBytes)
        }

    private const val MAXIMUM_DECODED_BYTES: Int = 64 * 1024 * 1024
}
