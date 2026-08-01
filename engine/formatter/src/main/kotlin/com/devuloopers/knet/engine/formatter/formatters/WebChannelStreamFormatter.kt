package com.devuloopers.knet.engine.formatter.formatters

import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat

/**
 * Strategy formatter for Firestore and gRPC WebChannel length-prefixed stream frames.
 */
class WebChannelStreamFormatter(
    private val jsonFormatter: JsonBodyFormatter = JsonBodyFormatter()
) : BodyFormatter {
    override val priority: Int = 90

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()
        val trimmed = bodyText.trim()

        return mime.contains("grpc") || mime.contains("channel") ||
                (trimmed.contains("[[") && trimmed.contains("]]") && (trimmed.contains("noop") || trimmed.contains("google.firestore")))
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val trimmed = bodyText.trim()
        if (trimmed.contains("{") && trimmed.contains("}")) {
            val cleanedChunks = mutableListOf<String>()
            val frameRegex = Regex("""\[\[[\s\S]*?]](?:\d+|$)""")
            val matches = frameRegex.findAll(trimmed)

            for (match in matches) {
                val rawFrame = match.value.replace(Regex("""\d+$"""), "").trim()
                val formattedFrame = jsonFormatter.prettyPrintJson(rawFrame)
                cleanedChunks.add(formattedFrame)
            }

            val frames = cleanedChunks.ifEmpty { listOf(trimmed) }
            return BodyFormat.JsonStream(frames)
        }
        return BodyFormat.JsonStream(if (trimmed.isEmpty()) emptyList() else listOf(trimmed))
    }
}
