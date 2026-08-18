package com.devuloopers.knet.domain.clientNetwork.decoder

import com.devuloopers.knet.traffic.model.body.ContentEncoding

/**
 * Strategy interface for content-encoding decompression implementations.
 */
interface ContentDecoder {
    /**
     * The [ContentEncoding] enum identifier supported by this decompressor.
     */
    val encoding: ContentEncoding

    /**
     * Decompresses the provided byte array payload.
     *
     * @param bytes Compressed input bytes.
     * @return Decompressed output bytes.
     * @throws Exception if decompression fails due to stream corruption.
     */
    fun decompress(bytes: ByteArray): ByteArray
}
