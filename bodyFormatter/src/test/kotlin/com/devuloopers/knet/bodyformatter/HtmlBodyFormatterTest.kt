package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.HtmlBodyFormatter
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlBodyFormatterTest {

    private val formatter = HtmlBodyFormatter()

    @Test
    fun testMatchesHtmlContentType() {
        val headers = mapOf("content-type" to "text/html; charset=utf-8")
        val body = "<HTML><HEAD><meta http-equiv=\"content-type\" content=\"text/html;charset=utf-8\"><TITLE>302 Moved</TITLE></HEAD><BODY><H1>302 Moved</H1>The document has moved</BODY></HTML>"
        assertTrue(formatter.matches(headers, body))
    }

    @Test
    fun testPrettyPrintHtml302Response() {
        val headers = mapOf("content-type" to "text/html; charset=utf-8")
        val rawHtml = "<HTML><HEAD><meta http-equiv=\"content-type\" content=\"text/html;charset=utf-8\"><TITLE>302 Moved</TITLE></HEAD><BODY><H1>302 Moved</H1>The document has moved</BODY></HTML>"
        
        val result = formatter.format(headers, rawHtml)
        assertTrue(result is BodyFormat.Html)
        assertEquals("HTML", result.badgeLabel)
        
        val pretty = (result as BodyFormat.Html).formattedText
        assertTrue(pretty.contains("\n"), "Formatted HTML must span across multiple lines")
        assertTrue(pretty.contains("  <HEAD>"), "Nested tags must be indented")
        assertTrue(pretty.contains("  <BODY>"), "Body tag must be indented")
    }

    @Test
    fun testMatchesXmlContentType() {
        val headers = mapOf("content-type" to "application/xml")
        val body = "<?xml version=\"1.0\"?><note><to>User</to><from>KNet</from></note>"
        val result = formatter.format(headers, body)
        assertTrue(result is BodyFormat.Xml)
        assertEquals("XML", result.badgeLabel)
    }
}
