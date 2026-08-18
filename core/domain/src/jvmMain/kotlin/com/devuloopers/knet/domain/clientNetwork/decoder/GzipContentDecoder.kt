package com.devuloopers.knet.domain.clientNetwork.decoder

import com.devuloopers.knet.traffic.model.body.ContentEncoding

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * JVM strategy implementation for GZIP content decompression.
 */
class GzipContentDecoder : ContentDecoder {
    override val encoding: ContentEncoding = ContentEncoding.GZIP

    override fun decompress(bytes: ByteArray): ByteArray {
        ByteArrayInputStream(bytes).use { bais ->
            GZIPInputStream(bais).use { gzis ->
                val baos = ByteArrayOutputStream()
                gzis.copyTo(baos)
                return baos.toByteArray()
            }
        }
    }
}
