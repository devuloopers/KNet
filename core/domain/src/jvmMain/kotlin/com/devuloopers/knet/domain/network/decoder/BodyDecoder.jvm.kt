package com.devuloopers.knet.domain.network.decoder

/**
 * JVM implementation of [BodyDecoder] using composable SPI [ContentDecoder] strategy implementations.
 */
public actual object BodyDecoder {

    private val decoders: Map<ContentEncoding, ContentDecoder> = listOf(
        GzipContentDecoder(),
        DeflateContentDecoder(),
        BrotliContentDecoder(),
        ZstdContentDecoder()
    ).associateBy { it.encoding }

    public actual fun decode(body: ByteArray?, headers: List<Pair<String, String>>): DecodedBodyResult {
        if (body == null || body.isEmpty()) {
            return DecodedBodyResult.Identity(ByteArray(0))
        }

        val encodingHeader = headers.firstOrNull { it.first.equals("Content-Encoding", ignoreCase = true) }?.second?.trim()?.lowercase()
            ?: return DecodedBodyResult.Identity(body)

        if (encodingHeader.isBlank() || encodingHeader == "identity") {
            return DecodedBodyResult.Identity(body)
        }

        val encodingChain = encodingHeader.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("identity", ignoreCase = true) }

        if (encodingChain.isEmpty()) {
            return DecodedBodyResult.Identity(body)
        }

        var currentBytes: ByteArray = body
        var lastResolvedEncoding: ContentEncoding = ContentEncoding.IDENTITY

        // Process encoding chain in reverse order (right-to-left) as specified by HTTP RFC 7231
        for (token in encodingChain.reversed()) {
            val enumEncoding = ContentEncoding.fromHeaderValue(token)
                ?: return DecodedBodyResult.UnsupportedEncoding(token, body)

            val decoder = decoders[enumEncoding]
                ?: return DecodedBodyResult.UnsupportedEncoding(token, body)

            currentBytes = try {
                decoder.decompress(currentBytes)
            } catch (ex: Throwable) {
                return DecodedBodyResult.CorruptedEncoding(token, ex.message ?: "Decompression failed for $token", body)
            }

            lastResolvedEncoding = enumEncoding
        }

        return DecodedBodyResult.Success(currentBytes, lastResolvedEncoding)
    }
}
