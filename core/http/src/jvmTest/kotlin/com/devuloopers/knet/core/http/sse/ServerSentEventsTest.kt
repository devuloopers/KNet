package com.devuloopers.knet.core.http.sse

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerSentEventsTest {

    @Test
    fun testEventStreamLineParsing() {
        val sseChunk = "data: {\"event\": \"user_connected\", \"id\": 42}\n\n"
        val lines = sseChunk.trim().split("\n")

        assertEquals(1, lines.size)
        assertEquals("data: {\"event\": \"user_connected\", \"id\": 42}", lines[0])
    }
}
