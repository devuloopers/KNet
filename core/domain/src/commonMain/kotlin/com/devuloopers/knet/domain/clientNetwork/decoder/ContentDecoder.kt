package com.devuloopers.knet.domain.clientNetwork.decoder

/**
 * Strategy interface for content-encoding decompression implementations.
 */
public interface ContentDecoder {
    /**
     * The [ContentEncoding] enum identifier supported by this decompressor.
     */
    public val encoding: ContentEncoding

    /**
     * Decompresses the provided byte array payload.
     *
     * @param bytes Compressed input bytes.
     * @return Decompressed output bytes.
     * @throws Exception if decompression fails due to stream corruption.
     */
    public fun decompress(bytes: ByteArray): ByteArray
}
