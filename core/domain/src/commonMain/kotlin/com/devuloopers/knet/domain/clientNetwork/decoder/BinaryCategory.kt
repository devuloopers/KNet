package com.devuloopers.knet.domain.clientNetwork.decoder

/**
 * Domain enum representing known categories of binary HTTP payload content.
 */
public enum class BinaryCategory {
    /**
     * Oblivious HTTP Encrypted Request/Response (RFC 9458 / RFC 9180 HPKE).
     */
    OHTTP,

    /**
     * Protocol Buffers binary serialization.
     */
    PROTOBUF,

    /**
     * Concise Binary Object Representation (RFC 8949).
     */
    CBOR,

    /**
     * MessagePack binary serialization format.
     */
    MSGPACK,

    /**
     * WebAssembly binary bytecode format.
     */
    WASM,

    /**
     * Compressed archive formats (ZIP, 7z, GZIP archive, etc.).
     */
    ARCHIVE,

    /**
     * Image binary data (PNG, JPEG, WebP, GIF, etc.).
     */
    IMAGE,

    /**
     * Audio binary stream (MP3, AAC, WAV, OGG).
     */
    AUDIO,

    /**
     * Video binary stream (MP4, WebM, AVI).
     */
    VIDEO,

    /**
     * Font file format (WOFF, WOFF2, TTF, OTF).
     */
    FONT,

    /**
     * Generic or unspecified binary content.
     */
    GENERIC
}
