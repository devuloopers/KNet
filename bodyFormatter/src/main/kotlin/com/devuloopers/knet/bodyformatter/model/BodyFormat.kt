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
    data class RawText(val text: String) : BodyFormat()

    val badgeLabel: String
        get() = when (this) {
            is Json -> "JSON"
            is JsonStream -> "JSON Stream"
            is FormData -> "Form Data"
            is SseStream -> "SSE Stream"
            is Protobuf -> "Protobuf"
            is Image -> label
            is RawText -> "PLAIN"
        }
}
