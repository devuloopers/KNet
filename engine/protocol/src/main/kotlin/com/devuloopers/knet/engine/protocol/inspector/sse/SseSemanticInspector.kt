package com.devuloopers.knet.engine.protocol.inspector.sse

import com.devuloopers.knet.application.port.inspection.SemanticInspectionInput
import com.devuloopers.knet.application.port.inspection.SemanticInspector
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectionField
import com.devuloopers.knet.traffic.inspection.InspectorId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import java.io.ByteArrayOutputStream

/** Bounded post-capture SSE semantic inspector; forwarding remains incremental and untouched. */
class SseSemanticInspector : SemanticInspector {
    override val id: InspectorId = InspectorId("sse")
    override val schemaVersion: Long = 1L
    override val priority: Int = 90
    override val bodyBudgetBytes: Int = 1_048_576

    override fun supports(exchange: HttpExchangeSnapshot): Boolean =
        exchange.response?.head?.headers.orEmpty().any { header ->
            header.name.value.equals("Content-Type", ignoreCase = true) &&
                header.value.substringBefore(';').trim().equals("text/event-stream", ignoreCase = true)
        }

    override suspend fun inspect(input: SemanticInspectionInput): InspectionDocument? {
        if (!supports(input.exchange)) return null
        val bytes = ByteArrayOutputStream(input.responseBody?.size ?: 0).use { output ->
            input.responseBody?.chunks.orEmpty().forEach { output.write(it.copyBytes()) }
            output.toByteArray()
        }
        val events = parse(bytes.decodeToString())
        val namedTypes = events.mapNotNull(Event::type).distinct().take(32)
        val firstData = events.firstOrNull()?.data?.take(256)?.takeIf(String::isNotBlank)
        return InspectionDocument(
            kind = "sse",
            title = "Server-Sent Events (${events.size})",
            summary = firstData,
            fields = buildList {
                add(InspectionField("Parsed events", events.size.toString()))
                if (namedTypes.isNotEmpty()) add(InspectionField("Event types", namedTypes.joinToString(", ")))
                input.responseBody?.truncated?.takeIf { it }?.let {
                    add(InspectionField("Body", "Preview truncated"))
                }
            },
        )
    }

    private data class Event(val type: String?, val data: String)

    private fun parse(text: String): List<Event> {
        val events = ArrayList<Event>(minOf(MAX_EVENTS, 16))
        var type: String? = null
        val data = StringBuilder()
        fun finish() {
            if ((type != null || data.isNotEmpty()) && events.size < MAX_EVENTS) {
                events += Event(type, data.toString().removeSuffix("\n"))
            }
            type = null
            data.clear()
        }
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.removeSuffix("\r")
            if (line.isEmpty()) {
                finish()
            } else if (!line.startsWith(':')) {
                val field = line.substringBefore(':')
                val value = line.substringAfter(':', "").removePrefix(" ")
                when (field) {
                    "event" -> type = value.take(MAX_FIELD_CHARS)
                    "data" -> if (data.length < MAX_DATA_CHARS) data.append(value.take(MAX_DATA_CHARS - data.length)).append('\n')
                }
            }
        }
        finish()
        return events
    }

    private companion object {
        private const val MAX_EVENTS: Int = 64
        private const val MAX_FIELD_CHARS: Int = 128
        private const val MAX_DATA_CHARS: Int = 4_096
    }
}
