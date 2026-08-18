package com.devuloopers.knet.domain.clientNetwork.decoder

import com.devuloopers.knet.traffic.model.body.ContentEncoding

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * JVM strategy implementation for Brotli (`br`) content decompression using Google Brotli decoder.
 */
class BrotliContentDecoder : ContentDecoder {
    override val encoding: ContentEncoding = ContentEncoding.BROTLI

    override fun decompress(bytes: ByteArray): ByteArray {
        ByteArrayInputStream(bytes).use { bais ->
            BrotliInputStream(bais).use { bis ->
                val baos = ByteArrayOutputStream()
                bis.copyTo(baos)
                return baos.toByteArray()
            }
        }
    }
}
