package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.JsBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertTrue

class JsBodyFormatterTest {
    private val formatter = JsBodyFormatter()

    @Test
    fun testJsMatchingAndFormatting() {
        assertTrue(formatter.matches(mapOf("content-type" to "application/javascript"), TestFixtures.SAMPLE_JS))

        val result = formatter.format(mapOf("content-type" to "application/javascript"), TestFixtures.SAMPLE_JS)
        assertTrue(result is BodyFormat.Js)
        assertTrue(result.formattedText.contains("function greet"))
    }
}
