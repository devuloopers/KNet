package com.devuloopers.knet.domain.clientNetwork.decoder

import com.github.luben.zstd.ZstdInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * JVM strategy implementation for Zstandard (`zstd`) content decompression using Zstd-JNI.
 */
public class ZstdContentDecoder : ContentDecoder {
    override val encoding: ContentEncoding = ContentEncoding.ZSTD

    override fun decompress(bytes: ByteArray): ByteArray {
        ByteArrayInputStream(bytes).use { bais ->
            ZstdInputStream(bais).use { zis ->
                val baos = ByteArrayOutputStream()
                zis.copyTo(baos)
                return baos.toByteArray()
            }
        }
    }
}
