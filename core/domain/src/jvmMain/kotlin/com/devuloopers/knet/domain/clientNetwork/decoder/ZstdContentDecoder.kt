package com.devuloopers.knet.domain.clientNetwork.decoder

import com.devuloopers.knet.traffic.model.body.ContentEncoding

import com.github.luben.zstd.ZstdInputStream
import java.io.ByteArrayInputStream

/**
 * JVM strategy implementation for Zstandard (`zstd`) content decompression using Zstd-JNI.
 */
class ZstdContentDecoder : ContentDecoder {
    override val encoding: ContentEncoding = ContentEncoding.ZSTD

    override fun decompress(bytes: ByteArray, maximumOutputBytes: Int): ByteArray {
        ByteArrayInputStream(bytes).use { input ->
            ZstdInputStream(input).use { decoded ->
                return decoded.readBoundedBytes(maximumOutputBytes)
            }
        }
    }
}
