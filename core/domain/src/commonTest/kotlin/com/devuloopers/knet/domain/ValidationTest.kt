package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.util.decodeBodyToText
import com.devuloopers.knet.domain.util.formatJsonIfPossible
import com.devuloopers.knet.domain.util.isBinaryContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationTest {

    @Test
    fun testIsBinaryContentTypeDetection() {
        assertTrue(isBinaryContentType("image/png"))
        assertTrue(isBinaryContentType("image/jpeg"))
        assertTrue(isBinaryContentType("application/x-protobuf"))
        assertTrue(isBinaryContentType("application/protobuf"))
        assertTrue(isBinaryContentType("application/octet-stream"))
        assertTrue(isBinaryContentType("application/grpc"))
        assertTrue(isBinaryContentType("audio/mp3"))
        assertTrue(isBinaryContentType("video/mp4"))
        assertTrue(isBinaryContentType("font/woff2"))

        assertFalse(isBinaryContentType("application/json"))
        assertFalse(isBinaryContentType("text/html"))
        assertFalse(isBinaryContentType("text/plain"))
        assertFalse(isBinaryContentType("application/xml"))
        assertFalse(isBinaryContentType(null))
    }

    @Test
    fun testDecodeBodyToTextWithTextPayload() {
        val bytes = "{\"status\":\"ok\"}".encodeToByteArray()
        val text = decodeBodyToText(bytes, listOf("Content-Type" to "application/json"))
        assertEquals("{\"status\":\"ok\"}", text)
    }

    @Test
    fun testDecodeBodyToTextWithBinaryPayload() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val text = decodeBodyToText(bytes, listOf("Content-Type" to "image/png"))
        assertEquals("[Binary Payload - 4 B (IMAGE)]", text)
    }

    @Test
    fun testDecodeBodyToTextWithNullOrEmpty() {
        assertEquals("", decodeBodyToText(null))
        assertEquals("", decodeBodyToText(byteArrayOf()))
    }

    @Test
    fun testFormatJsonIfPossibleValidJson() {
        val raw = "{\"name\":\"KNet\",\"version\":1}"
        val formatted = formatJsonIfPossible(raw)
        assertTrue(formatted.contains("\n"))
        assertTrue(formatted.contains("\"name\": \"KNet\""))
    }

    @Test
    fun testFormatJsonIfPossibleInvalidJsonReturnsRaw() {
        val raw = "{ invalid json syntax }"
        assertEquals("{ invalid json syntax }", formatJsonIfPossible(raw))
    }
}
