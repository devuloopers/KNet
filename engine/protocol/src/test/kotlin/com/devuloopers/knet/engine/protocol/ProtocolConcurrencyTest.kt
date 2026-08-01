package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.engine.protocol.websocket.WebSocketFrameParser
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class ProtocolConcurrencyTest {

    @Test
    fun testConcurrentWebSocketFrameParsing() {
        val parser = WebSocketFrameParser()
        val executor = Executors.newFixedThreadPool(10)

        repeat(100) { i ->
            executor.submit {
                val frame = TestFixtures.createTextWebSocketFrame("concurrent_msg_$i")
                parser.parseFrame(frame, FrameDirection.CLIENT_TO_SERVER)
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(10, TimeUnit.SECONDS)
        assertTrue(finished, "Concurrent WebSocket frame parsing must complete within timeout")
    }
}
