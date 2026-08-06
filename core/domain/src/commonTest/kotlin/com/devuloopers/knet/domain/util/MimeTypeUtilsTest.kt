package com.devuloopers.knet.domain.util

import com.devuloopers.knet.domain.clientNetwork.model.MimeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MimeTypeUtilsTest {

    @Test
    fun testExtractFromHeadersCaseInsensitive() {
        val headers = mapOf("Content-Type" to "application/json; charset=utf-8")
        val mime = MimeTypeUtils.extractFromHeaders(headers)
        assertEquals(MimeType.APPLICATION_JSON, mime)
        assertEquals("application/json", mime.value)
    }

    @Test
    fun testExtractFromHeadersDefaultFallback() {
        val headers = mapOf("Server" to "KNet/1.0")
        val mime = MimeTypeUtils.extractFromHeaders(headers, defaultMime = MimeType.TEXT_HTML)
        assertEquals(MimeType.TEXT_HTML, mime)
    }

    @Test
    fun testParsePrimaryMimeTypeStripsParameters() {
        assertEquals(MimeType.APPLICATION_XML, MimeTypeUtils.parsePrimaryMimeType("application/xml; charset=UTF-8; boundary=something"))
        assertEquals(MimeType.TEXT_PLAIN, MimeTypeUtils.parsePrimaryMimeType(""))
    }

    @Test
    fun testIsJsonDetection() {
        assertTrue(MimeTypeUtils.isJson(MimeType.APPLICATION_JSON))
        assertTrue(MimeTypeUtils.isJson(MimeType.TEXT_PLAIN, rawBody = "{\"success\": true}"))
        assertFalse(MimeTypeUtils.isJson(MimeType.TEXT_PLAIN, rawBody = "Hello world"))
    }

    @Test
    fun testIsXmlOrHtmlDetection() {
        assertTrue(MimeTypeUtils.isXmlOrHtml(MimeType.APPLICATION_XML))
        assertTrue(MimeTypeUtils.isXmlOrHtml(MimeType.TEXT_PLAIN, rawBody = "<root></root>"))
        assertFalse(MimeTypeUtils.isXmlOrHtml(MimeType.TEXT_PLAIN, rawBody = "Plain text"))
    }
}
