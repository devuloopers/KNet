package com.devuloopers.knet.engine.sse.inspection

import com.devuloopers.knet.application.contract.inspection.SemanticInspectionInput
import com.devuloopers.knet.application.contract.inspection.SemanticInspector
import com.devuloopers.knet.engine.sse.protocol.SseIncrementalParser
import com.devuloopers.knet.engine.sse.protocol.SseLimits
import com.devuloopers.knet.engine.sse.protocol.SseParseResult
import com.devuloopers.knet.engine.sse.protocol.SseProtocol
import com.devuloopers.knet.engine.sse.protocol.SseRecordKind
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectionField
import com.devuloopers.knet.traffic.inspection.InspectorId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot

/** Historical bounded SSE annotation adapter backed by the same parser used for live records. */
class SseSemanticInspector(
    private val limits: SseLimits = SseLimits(),
) : SemanticInspector {
    override val id: InspectorId = InspectorId("sse")
    override val schemaVersion: Long = 2L
    override val priority: Int = 90
    override val bodyBudgetBytes: Int = limits.maximumRecordBytes

    override fun supports(exchange: HttpExchangeSnapshot): Boolean =
        SseProtocol.isEventStream(exchange.response?.head?.headers.orEmpty())

    override suspend fun inspect(input: SemanticInspectionInput): InspectionDocument? {
        if (!supports(input.exchange)) return null
        val parser = SseIncrementalParser(limits)
        val records = buildList {
            input.responseBody?.chunks.orEmpty().forEach { chunk ->
                parser.accept(chunk.copyBytes()).forEach { result ->
                    if (result is SseParseResult.Record) add(result.value)
                }
            }
            parser.finish().forEach { result ->
                if (result is SseParseResult.Record) add(result.value)
            }
        }.take(MAXIMUM_SUMMARY_RECORDS)
        val events = records.filter { it.kind == SseRecordKind.EVENT }
        val types = events.mapNotNull { it.eventType }.distinct().take(MAXIMUM_SUMMARY_TYPES)
        return InspectionDocument(
            kind = "sse",
            title = "Server-Sent Events (${events.size})",
            summary = events.firstOrNull()?.data?.take(MAXIMUM_SUMMARY_CHARACTERS)?.takeIf(String::isNotBlank),
            fields = buildList {
                add(InspectionField("Parsed events", events.size.toString()))
                add(InspectionField("Control records", (records.size - events.size).toString()))
                if (types.isNotEmpty()) add(InspectionField("Event types", types.joinToString(", ")))
                if (input.responseBody?.truncated == true) add(InspectionField("Body", "Preview truncated"))
            },
        )
    }

    private companion object {
        const val MAXIMUM_SUMMARY_RECORDS: Int = 64
        const val MAXIMUM_SUMMARY_TYPES: Int = 32
        const val MAXIMUM_SUMMARY_CHARACTERS: Int = 256
    }
}
