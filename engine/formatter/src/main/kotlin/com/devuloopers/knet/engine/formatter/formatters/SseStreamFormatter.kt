package com.devuloopers.knet.engine.formatter.formatters

import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.sse.protocol.SseIncrementalParser
import com.devuloopers.knet.engine.sse.protocol.SseParseResult
import com.devuloopers.knet.engine.sse.protocol.SseProtocol

/**
 * Strategy formatter for Server-Sent Events (`text/event-stream` / `data:` event lines).
 */
class SseStreamFormatter : BodyFormatter {
    override val priority: Int = 80

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val trimmed = bodyText.trim()

        return SseProtocol.isEventStream(contentType) || trimmed.startsWith("data:") || trimmed.startsWith("event:")
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val parser = SseIncrementalParser()
        val events = parser.accept(bodyText.encodeToByteArray()).mapNotNull { result ->
            (result as? SseParseResult.Record)?.value?.copyRawRecord()?.decodeToString()?.trimEnd('\r', '\n')
        }
        return BodyFormat.SseStream(events)
    }
}
