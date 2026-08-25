package com.devuloopers.knet.engine.sse.inspection

import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadDecoder
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadDecoderId
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadInput
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.engine.sse.protocol.SseIncrementalParser
import com.devuloopers.knet.engine.sse.protocol.SseLimits
import com.devuloopers.knet.engine.sse.protocol.SseParseResult
import com.devuloopers.knet.engine.sse.protocol.SseRecordKind
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind

/** Shared-parser SSE decoder plugged into Traffic's protocol-neutral presentation registry. */
class SseProtocolMessageDecoder(
    private val limits: SseLimits = SseLimits(),
) : ProtocolMessagePayloadDecoder {
    override val decoderId: ProtocolMessagePayloadDecoderId = ProtocolMessagePayloadDecoderId("sse-record")
    override val protocolId: MessageProtocolId = MessageProtocolId.SSE
    override val priority: Int = 100

    override fun decode(input: ProtocolMessagePayloadInput): ProtocolMessagePresentation? {
        if (input.message.kind != ProtocolMessageKind.RECORD) return null
        val parser = SseIncrementalParser(limits)
        val parsed = (parser.accept(input.payload).firstOrNull() as? SseParseResult.Record)?.value ?: return null
        val title = when (parsed.kind) {
            SseRecordKind.EVENT -> "SSE ${parsed.eventType}"
            SseRecordKind.COMMENT -> "SSE keep-alive"
            SseRecordKind.STATE_UPDATE -> "SSE state update"
        }
        return ProtocolMessagePresentation(
            title = title,
            contentType = "text/event-stream",
            text = input.payload.decodeToString(),
            schemaName = parsed.lastEventId.takeIf(String::isNotEmpty),
        )
    }
}
