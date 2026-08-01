package com.devuloopers.knet.engine.formatter.formatters

import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat

/**
 * Strategy formatter for Server-Sent Events (`text/event-stream` / `data:` event lines).
 */
class SseStreamFormatter : BodyFormatter {
    override val priority: Int = 80

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()
        val trimmed = bodyText.trim()

        return mime.contains("event-stream") || trimmed.startsWith("data:") || trimmed.startsWith("event:")
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val events = bodyText.trim().lines().filter { it.trim().isNotEmpty() }
        return BodyFormat.SseStream(events)
    }
}
