package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.XmlBodyFormatter
import kotlin.test.Test
import kotlin.test.assertTrue

class XmlBodyFormatterTest {

    private val formatter = XmlBodyFormatter()

    @Test
    fun testMatchesXmlContentTypesAndTags() {
        assertTrue(formatter.matches(mapOf("content-type" to "application/xml"), "<root></root>"))
        assertTrue(formatter.matches(mapOf("content-type" to "text/xml"), "<root></root>"))
        assertTrue(formatter.matches(mapOf("content-type" to "application/soap+xml"), "<soap:Envelope></soap:Envelope>"))
        assertTrue(formatter.matches(emptyMap(), "<?xml version=\"1.0\"?><data/>"))
    }

    @Test
    fun testPrettyPrintIndentsXmlTree() {
        val minifiedXml = "<?xml version=\"1.0\"?><root><user id=\"1\"><name>John</name></user></root>"
        val formatted = formatter.prettyPrint(minifiedXml)

        assertTrue(formatted.contains("<user"))
        assertTrue(formatted.contains("<name>John</name>"))
    }

    @Test
    fun testPrettyPrintHandlesMalformedXmlGracefully() {
        val malformedXml = "<root><unclosedTag><name>Test</name></root>"
        val formatted = formatter.prettyPrint(malformedXml)

        assertTrue(formatted.contains("<unclosedTag>"))
        assertTrue(formatted.contains("<name>Test</name>"))
    }
}
