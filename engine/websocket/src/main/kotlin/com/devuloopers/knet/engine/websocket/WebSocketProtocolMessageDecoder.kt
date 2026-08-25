package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadDecoder
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadDecoderId
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadInput
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind

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
        ProtocolMessageKind.CLOSE -> closePresentation(input.payload)
        ProtocolMessageKind.BINARY -> null
        else -> null
    }

    private fun controlPresentation(label: String, payload: ByteArray): ProtocolMessagePresentation =
        ProtocolMessagePresentation(
            title = "WebSocket $label",
            contentType = "text/plain; charset=UTF-8",
            text = payload.decodeToString(),
        )

    private fun closePresentation(payload: ByteArray): ProtocolMessagePresentation {
        val code = if (payload.size >= 2) {
            ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff)
        } else {
            null
        }
        val reason = if (payload.size > 2) payload.copyOfRange(2, payload.size).decodeToString() else ""
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
        )
    }

    private fun detectTextContentType(payload: ByteArray): String {
        val first = payload.firstOrNull { byte -> !byte.toInt().toChar().isWhitespace() }?.toInt()?.toChar()
        return if (first == '{' || first == '[') "application/json" else "text/plain; charset=UTF-8"
    }
}
