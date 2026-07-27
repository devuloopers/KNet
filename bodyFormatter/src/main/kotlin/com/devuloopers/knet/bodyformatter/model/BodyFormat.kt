package com.devuloopers.knet.bodyformatter.model

/**
 * Strongly-typed representation of an HTTP payload body format.
 */
sealed class BodyFormat {
    data class Json(val formattedText: String) : BodyFormat()
    data class JsonStream(val frames: List<String>) : BodyFormat()
    data class FormData(val pairs: List<Pair<String, String>>) : BodyFormat()
    data class SseStream(val events: List<String>) : BodyFormat()
    data class Protobuf(val descriptor: String) : BodyFormat()
    data class Image(val label: String) : BodyFormat()
    data class Html(val formattedText: String) : BodyFormat()
    data class Xml(val formattedText: String) : BodyFormat()
    data class Cbor(val formattedText: String) : BodyFormat()
    data class Js(val formattedText: String) : BodyFormat()
    data class Css(val formattedText: String) : BodyFormat()
    data class GrpcWeb(val frames: List<GrpcWebFrame>) : BodyFormat()
    data class GraphQL(
        val operationType: String,
        val operationName: String?,
        val queryText: String,
        val variablesJson: String
    ) : BodyFormat()
    data class RawText(val text: String) : BodyFormat()

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

data class GrpcWebFrame(
    val isTrailer: Boolean,
    val payloadHex: String,
    val decodedJsonOrText: String
)

