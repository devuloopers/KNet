package com.devuloopers.knet.domain.clientNetwork.decoder

import com.devuloopers.knet.traffic.model.body.ContentEncoding

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

/**
 * JVM strategy implementation for Deflate content decompression.
 */
class DeflateContentDecoder : ContentDecoder {
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
