package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadDecoder
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadDecoderId
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadInput
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.inspection.InspectionField

/** Text-aware WebSocket payload decoder plugged into the common Traffic presentation registry. */
class WebSocketProtocolMessageDecoder : ProtocolMessagePayloadDecoder {
    override val decoderId: ProtocolMessagePayloadDecoderId = ProtocolMessagePayloadDecoderId("websocket-raw")
    override val protocolId: MessageProtocolId = MessageProtocolId.WEBSOCKET
    override val priority: Int = 0

    override fun decode(input: ProtocolMessagePayloadInput): ProtocolMessagePresentation? = when (input.message.kind) {
        ProtocolMessageKind.TEXT -> ProtocolMessagePresentation(
            title = "WebSocket text message",
            contentType = detectTextContentType(input.payload),
            text = input.payload.decodeToString(),
        )
        ProtocolMessageKind.PING -> controlPresentation("Ping", input.payload)
        ProtocolMessageKind.PONG -> controlPresentation("Pong", input.payload)
        ProtocolMessageKind.CLOSE -> if (input.truncated) null else closePresentation(input.payload)
        ProtocolMessageKind.BINARY -> null
        else -> null
    }

    private fun controlPresentation(label: String, payload: ByteArray): ProtocolMessagePresentation? {
        // Control payloads are arbitrary octets. Binary or invalid UTF-8 stays in the generic hex viewer.
        val text = runCatching { payload.decodeToString(throwOnInvalidSequence = true) }.getOrNull() ?: return null
        if (text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) return null
        return ProtocolMessagePresentation(
            title = "WebSocket $label",
            contentType = "text/plain; charset=UTF-8",
            text = text,
        )
    }

    private fun closePresentation(payload: ByteArray): ProtocolMessagePresentation? {
        if (payload.size == 1) return null
        val code = if (payload.size >= 2) {
            ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff)
        } else {
            null
        }
        val reason = if (payload.size > 2) runCatching {
            payload.copyOfRange(2, payload.size).decodeToString(throwOnInvalidSequence = true)
        }.getOrNull() ?: return null else ""
        val text = buildString {
            append("Code: ")
            append(code?.toString() ?: "not provided")
            if (reason.isNotBlank()) {
                append('\n')
                append("Reason: ")
                append(reason)
            }
        }
        return ProtocolMessagePresentation(
            title = "WebSocket close",
            contentType = "text/plain; charset=UTF-8",
            text = text,
            fields = buildList {
                code?.let { add(InspectionField("Close code", it.toString())) }
                if (reason.isNotBlank()) add(InspectionField("Close reason", reason))
            },
        )
    }

    private fun detectTextContentType(payload: ByteArray): String {
        val first = payload.firstOrNull { byte -> !byte.toInt().toChar().isWhitespace() }?.toInt()?.toChar()
        return if (first == '{' || first == '[') "application/json" else "text/plain; charset=UTF-8"
    }
}
