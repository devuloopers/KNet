package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.PlainTextBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlainTextBodyFormatterTest {
    private val formatter = PlainTextBodyFormatter()

    @Test
    fun testPlainTextFallback() {
        assertTrue(formatter.matches(emptyMap(), "plain text"))

        val result = formatter.format(emptyMap(), "plain text")
        assertTrue(result is BodyFormat.RawText)
        assertEquals("plain text", result.text)
    }
}
