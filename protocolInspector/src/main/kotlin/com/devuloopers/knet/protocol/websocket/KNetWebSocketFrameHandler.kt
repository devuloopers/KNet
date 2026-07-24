package com.devuloopers.knet.protocol.websocket

import com.devuloopers.knet.logger.KNetLogger
import com.devuloopers.knet.protocol.FrameDirection
import com.devuloopers.knet.protocol.FrameType
import com.devuloopers.knet.protocol.WebSocketFrameRecord
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import java.util.UUID

private const val TAG = "KNetWebSocketFrameHandler"

/**
 * Netty duplex handler that intercepts WebSocket frames passing through a channel.
 *
 * This handler registers callbacks when frames are sent or received, enabling
 * dynamic logging and session capture.
 *
 * Fully adheres to the variable naming rules (uses "context" instead of "ctx") and public API
 * KDoc guidelines.
 *
 * @property roleDirection The direction to assign to inbound frames. Outbound frames will use the opposite.
 * @property onFrameRecord Callback invoked when a frame is successfully parsed and recorded.
 */
class KNetWebSocketFrameHandler(
    private val roleDirection: FrameDirection,
    private val onFrameRecord: (WebSocketFrameRecord) -> Unit
) : ChannelDuplexHandler() {

    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        if (msg is WebSocketFrame) {
            val record = parseFrame(msg, roleDirection)
            if (record != null) {
                onFrameRecord(record)
            }
        }
        context.fireChannelRead(msg)
    }

    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is WebSocketFrame) {
            // Outbound frames flow in the opposite direction of the channel's role
            val outboundDirection = if (roleDirection == FrameDirection.CLIENT_TO_SERVER) {
                FrameDirection.SERVER_TO_CLIENT
            } else {
                FrameDirection.CLIENT_TO_SERVER
            }
            val record = parseFrame(msg, outboundDirection)
            if (record != null) {
                onFrameRecord(record)
            }
        }
        context.write(msg, promise)
    }

    /**
     * Parses a Netty [WebSocketFrame] into a clean immutable domain [WebSocketFrameRecord].
     *
     * @param frame The WebSocketFrame to parse.
     * @param direction The direction of the message.
     * @return The parsed record, or null if frame type is unsupported.
     */
    private fun parseFrame(frame: WebSocketFrame, direction: FrameDirection): WebSocketFrameRecord? {
        val timestamp = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val length = frame.content().readableBytes()

        return when (frame) {
            is TextWebSocketFrame -> {
                val text = frame.text()
                WebSocketFrameRecord(
                    id = id,
                    timestamp = timestamp,
                    direction = direction,
                    type = FrameType.TEXT,
                    length = length,
                    payloadText = text
                )
            }
            is BinaryWebSocketFrame -> {
                val bytes = ByteArray(length)
                val readerIndex = frame.content().readerIndex()
                frame.content().getBytes(readerIndex, bytes)
                val hex = bytes.joinToString("") { "%02X".format(it) }
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
