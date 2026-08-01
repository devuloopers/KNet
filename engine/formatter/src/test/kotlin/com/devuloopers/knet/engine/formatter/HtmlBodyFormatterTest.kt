package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.HtmlBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertTrue

class HtmlBodyFormatterTest {
    private val formatter = HtmlBodyFormatter()

    @Test
    fun testHtmlMatchingAndFormatting() {
        assertTrue(formatter.matches(mapOf("content-type" to "text/html"), TestFixtures.SAMPLE_HTML))

        val result = formatter.format(mapOf("content-type" to "text/html"), TestFixtures.SAMPLE_HTML)
        assertTrue(result is BodyFormat.Html)
        assertTrue(result.formattedText.contains("<h1>"))
        assertTrue(result.formattedText.contains("Hello KNet"))
    }
}
