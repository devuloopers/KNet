package com.devuloopers.knet.engine.protocol.websocket

import com.devuloopers.knet.engine.protocol.FrameDirection
import com.devuloopers.knet.engine.protocol.FrameType
import com.devuloopers.knet.engine.protocol.WebSocketFrameRecord
import com.devuloopers.knet.core.logger.KNetLogger
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import kotlin.uuid.Uuid

private const val TAG = "WebSocketFrameParser"

/**
 * Reusable WebSocket frame parser that decouples Netty frame parsing from channel handling.
 * Includes configurable payload size safeguards.
 *
 * @property maxInspectionBytes Maximum number of payload bytes to inspect/convert to prevent memory OOMs (default 1 MB).
 */
class WebSocketFrameParser(
    private val maxInspectionBytes: Int = 1_048_576
) {

    /**
     * Parses a Netty [WebSocketFrame] into a clean immutable domain [WebSocketFrameRecord].
     *
     * @param frame The Netty WebSocket frame to parse.
     * @param direction Transmission direction.
     * @return Domain record representation, or null if frame type is unsupported.
     */
    fun parseFrame(frame: WebSocketFrame, direction: FrameDirection): WebSocketFrameRecord? {
        val timestamp = System.currentTimeMillis()
        val id = Uuid.random().toString()
        val length = frame.content().readableBytes()

        return when (frame) {
            is TextWebSocketFrame -> {
                val text = frame.text()
                val truncatedText = if (text.length > maxInspectionBytes) {
                    text.take(maxInspectionBytes) + " ... [Truncated]"
                } else {
                    text
                }
                WebSocketFrameRecord(
                    id = id,
                    timestamp = timestamp,
                    direction = direction,
                    type = FrameType.TEXT,
                    length = length,
                    payloadText = truncatedText
                )
            }
            is BinaryWebSocketFrame -> {
                val inspectBytesCount = minOf(length, maxInspectionBytes)
                val bytes = ByteArray(inspectBytesCount)
                val readerIndex = frame.content().readerIndex()
                frame.content().getBytes(readerIndex, bytes)
                var hex = bytes.joinToString("") { "%02X".format(it) }
                if (length > maxInspectionBytes) {
                    hex += "... [Truncated]"
                }
                WebSocketFrameRecord(
                    id = id,
                    timestamp = timestamp,
                    direction = direction,
                    type = FrameType.BINARY,
                    length = length,
                    payloadHex = hex
                )
            }
            is CloseWebSocketFrame -> {
                WebSocketFrameRecord(
                    id = id,
                    timestamp = timestamp,
                    direction = direction,
                    type = FrameType.CLOSE,
                    length = length,
                    payloadText = "Close code: ${frame.statusCode()}, Reason: ${frame.reasonText()}"
                )
            }
            is PingWebSocketFrame -> {
                WebSocketFrameRecord(
                    id = id,
                    timestamp = timestamp,
                    direction = direction,
                    type = FrameType.PING,
                    length = length
                )
            }
            is PongWebSocketFrame -> {
                WebSocketFrameRecord(
                    id = id,
                    timestamp = timestamp,
                    direction = direction,
                    type = FrameType.PONG,
                    length = length
                )
            }
            else -> {
                KNetLogger.debug(TAG) { "Unsupported WebSocket frame class: ${frame::class.simpleName}" }
                null
            }
        }
    }
}
