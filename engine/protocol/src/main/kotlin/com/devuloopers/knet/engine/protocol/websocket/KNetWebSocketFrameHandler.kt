package com.devuloopers.knet.engine.protocol.websocket

import com.devuloopers.knet.engine.protocol.FrameDirection
import com.devuloopers.knet.engine.protocol.WebSocketFrameRecord
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.websocketx.WebSocketFrame

/**
 * Netty duplex handler intercepting WebSocket frames passing through a channel.
 * Delegates frame parsing to [WebSocketFrameParser].
 *
 * @property roleDirection Direction assigned to inbound frames. Outbound frames use opposite direction.
 * @property parser Reusable WebSocket frame parser.
 * @property onFrameRecord Callback invoked when a frame is parsed and recorded.
 */
@ChannelHandler.Sharable
class KNetWebSocketFrameHandler(
    private val roleDirection: FrameDirection,
    private val parser: WebSocketFrameParser = WebSocketFrameParser(),
    private val onFrameRecord: (WebSocketFrameRecord) -> Unit
) : ChannelDuplexHandler() {

    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        if (msg is WebSocketFrame) {
            val record = parser.parseFrame(msg, roleDirection)
            if (record != null) {
                onFrameRecord(record)
            }
        }
        context.fireChannelRead(msg)
    }

    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is WebSocketFrame) {
            val outboundDirection = if (roleDirection == FrameDirection.CLIENT_TO_SERVER) {
                FrameDirection.SERVER_TO_CLIENT
            } else {
                FrameDirection.CLIENT_TO_SERVER
            }
            val record = parser.parseFrame(msg, outboundDirection)
            if (record != null) {
                onFrameRecord(record)
            }
        }
        context.write(msg, promise)
    }
}
