package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat

/**
 * 2-Stage Priority Dispatcher for payload format resolution.
 *
 * Stage 1: Fast O(1) Header Lookup (Content-Type matching for 95% of traffic).
 * Stage 2: Structural Inspection (Priority fallback matching for vague or missing headers).
 */
object BodyFormatterRegistry {
    private val jsonFormatter = JsonBodyFormatter()
    private val webChannelFormatter = WebChannelStreamFormatter(jsonFormatter)
    private val sseFormatter = SseStreamFormatter()
    private val formDataFormatter = FormDataBodyFormatter()
    private val protobufFormatter = ProtobufBinaryFormatter()
    private val imageFormatter = ImageBodyFormatter()
    private val plainTextFormatter = PlainTextBodyFormatter()

    private val formatters: List<BodyFormatter> = listOf(
        protobufFormatter,
        imageFormatter,
        webChannelFormatter,
        sseFormatter,
        jsonFormatter,
        formDataFormatter,
        plainTextFormatter
    ).sortedByDescending { it.priority }

    /**
     * Resolves the strongly-typed [BodyFormat] for a given payload using the 2-stage dispatcher.
     */
    fun resolveFormat(headers: Map<String, String>, bodyText: String): BodyFormat {
        val trimmed = bodyText.trim()

        // Stage 1: Fast Header Lookup (Header matching happens even for empty body strings)
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()

        when {
            mime.startsWith("image/") -> return imageFormatter.format(headers, trimmed)
            mime.contains("x-www-form-urlencoded") -> return formDataFormatter.format(headers, trimmed)
            mime.contains("event-stream") -> return sseFormatter.format(headers, trimmed)
            mime.contains("grpc") || mime.contains("channel") -> return webChannelFormatter.format(headers, trimmed)
            mime.contains("proto") -> return protobufFormatter.format(headers, trimmed)
            mime.contains("json") -> return jsonFormatter.format(headers, trimmed)
        }

        if (trimmed.isEmpty()) return BodyFormat.RawText("")

        // Stage 2: Structural Inspection Fallback
        val matchedFormatter = formatters.firstOrNull { it.matches(headers, trimmed) } ?: plainTextFormatter
        return matchedFormatter.format(headers, trimmed)
    }

    /**
     * Formats a raw payload string into human-readable formatted text.
     */
    fun prettyPrintBody(headers: Map<String, String>, bodyText: String): String {
        return when (val format = resolveFormat(headers, bodyText)) {
            is BodyFormat.Json -> format.formattedText
            is BodyFormat.JsonStream -> format.frames.joinToString("\n\n")
            is BodyFormat.FormData -> format.pairs.joinToString("\n") { "${it.first} = ${it.second}" }
            is BodyFormat.SseStream -> format.events.joinToString("\n")
            is BodyFormat.Protobuf -> format.descriptor
            is BodyFormat.Image -> format.label
            is BodyFormat.RawText -> format.text
        }
    }
}
