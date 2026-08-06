package com.devuloopers.knet.domain.clientNetwork.decoder

/**
 * Pure domain Media Type (Content-Type) inspector stage.
 */
public object MediaTypeInspector {

    private val OHTTP_TYPES = setOf(
        "message/ohttp-req",
        "message/ohttp-res",
        "message/bhttp",
        "application/oblivious-dns-message"
    )

    private val PROTOBUF_TYPES = setOf(
        "application/x-protobuf",
        "application/protobuf",
        "application/vnd.google.protobuf",
        "application/grpc"
    )

    private val CBOR_TYPES = setOf(
        "application/cbor"
    )

    private val MSGPACK_TYPES = setOf(
        "application/msgpack",
        "application/x-msgpack"
    )

    private val WASM_TYPES = setOf(
        "application/wasm"
    )

    private val ARCHIVE_TYPES = setOf(
        "application/zip",
        "application/x-7z-compressed",
        "application/x-tar",
        "application/x-bzip",
        "application/x-bzip2",
        "application/x-rar-compressed"
    )

    /**
     * Inspects an optional HTTP `Content-Type` header string and returns the matching [BinaryCategory] if known binary, or null.
     *
     * @param contentType Optional Content-Type header value.
     * @return [BinaryCategory] if the Content-Type matches a known binary category, or null if text/unknown.
     */
    public fun inspectCategory(contentType: String?): BinaryCategory? {
        if (contentType.isNullOrBlank()) return null
        val lower = contentType.lowercase()

        return when {
            OHTTP_TYPES.any { lower.contains(it) } -> BinaryCategory.OHTTP
            PROTOBUF_TYPES.any { lower.contains(it) } -> BinaryCategory.PROTOBUF
            CBOR_TYPES.any { lower.contains(it) } -> BinaryCategory.CBOR
            MSGPACK_TYPES.any { lower.contains(it) } -> BinaryCategory.MSGPACK
            WASM_TYPES.any { lower.contains(it) } -> BinaryCategory.WASM
            ARCHIVE_TYPES.any { lower.contains(it) } -> BinaryCategory.ARCHIVE
            lower.contains("image/") -> BinaryCategory.IMAGE
            lower.contains("audio/") -> BinaryCategory.AUDIO
            lower.contains("video/") -> BinaryCategory.VIDEO
            lower.contains("font/") -> BinaryCategory.FONT
            lower.contains("application/octet-stream") -> BinaryCategory.GENERIC
            lower.startsWith("message/") -> BinaryCategory.GENERIC
            else -> null
        }
    }
}
