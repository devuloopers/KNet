package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.SseStreamFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SseStreamFormatterTest {
    private val formatter = SseStreamFormatter()

    @Test
    fun testSseStreamParsing() {
        val sseText = "data: {\"event\":\"ping\"}\n\ndata: {\"event\":\"pong\"}\n\n"
        assertTrue(formatter.matches(mapOf("content-type" to "text/event-stream"), sseText))

        val result = formatter.format(mapOf("content-type" to "text/event-stream"), sseText)
        assertTrue(result is BodyFormat.SseStream)
        assertEquals(2, result.events.size)
    }
}
