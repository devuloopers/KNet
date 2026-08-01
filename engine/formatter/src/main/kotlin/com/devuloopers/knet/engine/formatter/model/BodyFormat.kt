package com.devuloopers.knet.engine.formatter.model

/**
 * Strongly-typed representation of an HTTP payload body format result.
 * Used as the output of the [com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry] dispatcher.
 */
sealed class BodyFormat {
    /** Pretty-printed JSON object or array. */
    data class Json(val formattedText: String) : BodyFormat()
    /** Multi-frame JSON stream (e.g. NDJSON). */
    data class JsonStream(val frames: List<String>) : BodyFormat()
    /** URL-encoded or multipart form key-value pairs. */
    data class FormData(val pairs: List<Pair<String, String>>) : BodyFormat()
    /** Server-Sent Events stream event lines. */
    data class SseStream(val events: List<String>) : BodyFormat()
    /** Protobuf descriptor string (schema-based or raw wire decoded). */
    data class Protobuf(val descriptor: String) : BodyFormat()
    /** Image MIME label (PNG Image, JPEG Image, etc.). */
    data class Image(val label: String) : BodyFormat()
    /** Formatted HTML markup. */
    data class Html(val formattedText: String) : BodyFormat()
    /** Formatted XML markup. */
    data class Xml(val formattedText: String) : BodyFormat()
    /** Decoded CBOR payload as pretty-printed JSON string. */
    data class Cbor(val formattedText: String) : BodyFormat()
    /** Formatted JavaScript source code. */
    data class Js(val formattedText: String) : BodyFormat()
    /** Formatted CSS stylesheet. */
    data class Css(val formattedText: String) : BodyFormat()
    /** Parsed gRPC-Web binary frames. */
    data class GrpcWeb(val frames: List<GrpcWebFrame>) : BodyFormat()
    /** GraphQL operation type, name, query text, and variables JSON. */
    data class GraphQL(
        val operationType: String,
        val operationName: String?,
        val queryText: String,
        val variablesJson: String
    ) : BodyFormat()
    /** Raw plain text fallback. */
    data class RawText(val text: String) : BodyFormat()

    /**
     * Short human-readable badge label suitable for UI rendering.
     */
    val badgeLabel: String
        get() = when (this) {
            is Json -> "JSON"
            is JsonStream -> "JSON Stream"
            is FormData -> "Form Data"
            is SseStream -> "SSE Stream"
            is Protobuf -> "Protobuf"
            is Image -> label
            is Html -> "HTML"
            is Xml -> "XML"
            is Cbor -> "CBOR"
            is Js -> "JS"
            is Css -> "CSS"
            is GrpcWeb -> "gRPC-Web"
            is GraphQL -> if (!operationName.isNullOrEmpty()) "GQL: $operationName" else "GQL: $operationType"
            is RawText -> "PLAIN"
        }
}

/**
 * Represents a single parsed gRPC-Web binary frame.
 *
 * @property isTrailer True if this is a trailer frame (metadata/headers).
 * @property payloadHex Hexadecimal string representation of the raw frame bytes.
 * @property decodedJsonOrText Human-readable decoded payload content.
 */
data class GrpcWebFrame(
    val isTrailer: Boolean,
    val payloadHex: String,
    val decodedJsonOrText: String
)
