package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.engine.protocol.websocket.WebSocketFrameParser
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceRegressionTest {

    @Test
    fun testFrameParserPerformance() {
        val parser = WebSocketFrameParser()

        val duration = measureTimeMillis {
            repeat(1000) {
                val frame = TestFixtures.createTextWebSocketFrame("test_frame_$it")
                parser.parseFrame(frame, FrameDirection.CLIENT_TO_SERVER)
            }
        }

        assertTrue(duration < 1000, "1000 frame parsing operations must take under 1 second")
    }
}
