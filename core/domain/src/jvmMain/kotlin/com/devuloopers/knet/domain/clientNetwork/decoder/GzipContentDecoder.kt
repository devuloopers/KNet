package com.devuloopers.knet.domain.clientNetwork.decoder

import com.devuloopers.knet.traffic.model.body.ContentEncoding

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * JVM strategy implementation for GZIP content decompression.
 */
class GzipContentDecoder : ContentDecoder {
    override val encoding: ContentEncoding = ContentEncoding.GZIP

    override fun decompress(bytes: ByteArray, maximumOutputBytes: Int): ByteArray {
        ByteArrayInputStream(bytes).use { input ->
            GZIPInputStream(input).use { decoded ->
                return decoded.readBoundedBytes(maximumOutputBytes)
            }
        }
    }
}
