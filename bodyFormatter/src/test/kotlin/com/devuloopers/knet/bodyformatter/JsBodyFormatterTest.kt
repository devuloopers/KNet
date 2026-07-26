package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.JsBodyFormatter
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsBodyFormatterTest {
    private val formatter = JsBodyFormatter()

    @Test
    fun testMatchesJsContentType() {
        assertTrue(formatter.matches(mapOf("Content-Type" to "application/javascript"), ""))
        assertTrue(formatter.matches(mapOf("content-type" to "text/javascript; charset=utf-8"), ""))
    }

    @Test
    fun testFormatJsCodeBlock() {
        val rawJs = "function test(){const a=1;if(a===1){console.log('hi');}}"
        val result = formatter.format(emptyMap(), rawJs)

        assertTrue(result is BodyFormat.Js)
        val formattedText = result.formattedText
        println("FORMATTED JS:\n$formattedText")

        assertTrue(formattedText.contains("function test() {"))
        assertTrue(formattedText.contains("const a"))
        assertTrue(formattedText.contains("console.log('hi')"))
    }
}
