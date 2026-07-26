package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.XmlBodyFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlBodyFormatterTest {

    private val formatter = XmlBodyFormatter()

    @Test
    fun `matches returns true for xml content types and structural tags`() {
        assertTrue(formatter.matches(mapOf("content-type" to "application/xml"), "<root></root>"))
        assertTrue(formatter.matches(mapOf("content-type" to "text/xml"), "<root></root>"))
        assertTrue(formatter.matches(mapOf("content-type" to "application/soap+xml"), "<soap:Envelope></soap:Envelope>"))
        assertTrue(formatter.matches(emptyMap(), "<?xml version=\"1.0\"?><data/>"))
    }

    @Test
    fun `prettyPrint indents single-line xml into structured tree`() {
        val minifiedXml = "<?xml version=\"1.0\"?><root><user id=\"1\"><name>John</name></user></root>"
        val formatted = formatter.prettyPrint(minifiedXml)

        assertTrue(formatted.contains("<user id=\"1\">"))
        assertTrue(formatted.contains("<name>John</name>"))
    }

    @Test
    fun `prettyPrint handles malformed xml with graceful soft fallback`() {
        val malformedXml = "<root><unclosedTag><name>Test</name></root>"
        val formatted = formatter.prettyPrint(malformedXml)

        assertTrue(formatted.contains("<unclosedTag>"))
        assertTrue(formatted.contains("<name>Test</name>"))
    }
}
