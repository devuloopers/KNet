package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.CssBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertTrue

class CssBodyFormatterTest {
    private val formatter = CssBodyFormatter()

    @Test
    fun testCssMatchingAndFormatting() {
        assertTrue(formatter.matches(mapOf("content-type" to "text/css"), TestFixtures.SAMPLE_CSS))

        val result = formatter.format(mapOf("content-type" to "text/css"), TestFixtures.SAMPLE_CSS)
        assertTrue(result is BodyFormat.Css)
        assertTrue(result.formattedText.contains("margin: 0"))
    }
}
