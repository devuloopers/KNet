package com.devuloopers.knet.domain.network.decoder

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

/**
 * JVM strategy implementation for Deflate content decompression.
 */
public class DeflateContentDecoder : ContentDecoder {
    override val encoding: ContentEncoding = ContentEncoding.DEFLATE

    override fun decompress(bytes: ByteArray): ByteArray {
        ByteArrayInputStream(bytes).use { bais ->
            InflaterInputStream(bais).use { iis ->
                val baos = ByteArrayOutputStream()
                iis.copyTo(baos)
                return baos.toByteArray()
            }
        }
    }
}
