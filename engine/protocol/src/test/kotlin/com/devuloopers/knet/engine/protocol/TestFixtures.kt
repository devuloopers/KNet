package com.devuloopers.knet.engine.protocol

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame

object TestFixtures {

    fun createTextWebSocketFrame(text: String = "Hello WebSocket"): TextWebSocketFrame {
        return TextWebSocketFrame(text)
    }

    fun createBinaryWebSocketFrame(bytes: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04)): BinaryWebSocketFrame {
        return BinaryWebSocketFrame(Unpooled.copiedBuffer(bytes))
    }

    fun createCloseWebSocketFrame(statusCode: Int = 1000, reasonText: String = "Normal Closure"): CloseWebSocketFrame {
        return CloseWebSocketFrame(statusCode, reasonText)
    }

    fun createPingWebSocketFrame(): PingWebSocketFrame {
        return PingWebSocketFrame(Unpooled.copiedBuffer("ping_payload", Charsets.UTF_8))
    }

    fun createPongWebSocketFrame(): PongWebSocketFrame {
        return PongWebSocketFrame(Unpooled.copiedBuffer("pong_payload", Charsets.UTF_8))
    }
}
