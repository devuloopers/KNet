package com.devuloopers.knet.domain.clientNetwork.decoder

import com.devuloopers.knet.traffic.model.body.ContentEncoding

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream

/**
 * JVM strategy implementation for Brotli (`br`) content decompression using Google Brotli decoder.
 */
class BrotliContentDecoder : ContentDecoder {
    override val encoding: ContentEncoding = ContentEncoding.BROTLI

    override fun decompress(bytes: ByteArray, maximumOutputBytes: Int): ByteArray {
        ByteArrayInputStream(bytes).use { input ->
            BrotliInputStream(input).use { decoded ->
                return decoded.readBoundedBytes(maximumOutputBytes)
            }
        }
    }
}
