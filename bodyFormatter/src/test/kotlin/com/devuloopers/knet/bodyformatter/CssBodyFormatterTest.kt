package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.CssBodyFormatter
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CssBodyFormatterTest {
    private val formatter = CssBodyFormatter()

    @Test
    fun testMatchesCssContentType() {
        assertTrue(formatter.matches(mapOf("Content-Type" to "text/css"), ""))
        assertTrue(formatter.matches(mapOf("content-type" to "application/css; charset=UTF-8"), ""))
    }

    @Test
    fun testFormatCssSelectorAndRules() {
        val rawCss = "body{color:red;margin:0;}  .card  { padding:  10px; }"
        val result = formatter.format(emptyMap(), rawCss)

        assertTrue(result is BodyFormat.Css)
        val formattedText = result.formattedText

        assertTrue(formattedText.contains("body {"))
        assertTrue(formattedText.contains("color: red;"))
        assertTrue(formattedText.contains(".card {"))
        assertTrue(formattedText.contains("padding: 10px;"))
    }
}
