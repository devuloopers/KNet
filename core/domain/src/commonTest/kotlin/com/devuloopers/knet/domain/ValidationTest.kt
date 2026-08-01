package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.model.isUrlValid
import com.devuloopers.knet.domain.collection.model.isValidApiUrl
import com.devuloopers.knet.domain.util.decodeBodyToText
import com.devuloopers.knet.domain.util.formatJsonIfPossible
import com.devuloopers.knet.domain.util.isBinaryContentType
import com.devuloopers.knet.domain.validation.EnvironmentValidator
import com.devuloopers.knet.domain.validation.HeaderValidator
import com.devuloopers.knet.domain.validation.UrlValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationTest {

    @Test
    fun testIsValidApiUrlWithValidUrls() {
        assertTrue(isValidApiUrl("http://localhost:8080/api"))
        assertTrue(isValidApiUrl("https://api.knet.dev/v1/users"))
        assertTrue(isValidApiUrl("127.0.0.1:9090"))
        assertTrue(isValidApiUrl("localhost:3000"))
        assertTrue(isValidApiUrl("{{baseUrl}}/endpoint"))
        assertTrue(isValidApiUrl("subdomain.domain.org"))
    }

    @Test
    fun testIsValidApiUrlWithInvalidUrls() {
        assertFalse(isValidApiUrl(""))
        assertFalse(isValidApiUrl("   "))
        assertFalse(isValidApiUrl("invalid_url_no_dots_or_scheme"))
    }

    @Test
    fun testUrlValidatorDirectMethods() {
        assertTrue(UrlValidator.isValid("https://knet.dev"))
        assertFalse(UrlValidator.isValid(""))
    }

    @Test
    fun testHeaderValidatorDirectMethods() {
        assertTrue(HeaderValidator.isValidHeaderKey("Content-Type"))
        assertFalse(HeaderValidator.isValidHeaderKey("Bad Key Name"))
        assertTrue(HeaderValidator.isValidHeaderValue("application/json"))
    }

    @Test
    fun testEnvironmentValidatorDirectMethods() {
        assertTrue(EnvironmentValidator.isValidVariableKey("baseUrl"))
        assertTrue(EnvironmentValidator.isValidVariableKey("API_KEY"))
        assertFalse(EnvironmentValidator.isValidVariableKey("var with spaces"))
    }

    @Test
    fun testSavedApiRequestIsUrlValidExtension() {
        val validReq = TestFixtures.createSavedApiRequest(url = "https://knet.dev")
        val invalidReq = TestFixtures.createSavedApiRequest(url = "")

        assertTrue(validReq.isUrlValid)
        assertFalse(invalidReq.isUrlValid)
    }

    @Test
    fun testIsBinaryContentTypeDetection() {
        assertTrue(isBinaryContentType("application/octet-stream"))
        assertTrue(isBinaryContentType("application/x-protobuf"))
        assertTrue(isBinaryContentType("image/png"))
        assertTrue(isBinaryContentType("video/mp4"))
        assertTrue(isBinaryContentType("font/woff2"))

        assertFalse(isBinaryContentType("application/json"))
        assertFalse(isBinaryContentType("text/html"))
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
        assertEquals("[Binary Payload - 4 bytes]", text)
    }

    @Test
    fun testDecodeBodyToTextWithNullOrEmpty() {
        assertEquals("", decodeBodyToText(null))
        assertEquals("", decodeBodyToText(byteArrayOf()))
    }

    @Test
    fun testFormatJsonIfPossibleValidJson() {
        val rawJson = "{\"name\":\"KNet\",\"active\":true}"
        val formatted = formatJsonIfPossible(rawJson)

        assertTrue(formatted.contains("\n"))
        assertTrue(formatted.contains("\"name\": \"KNet\""))
    }

    @Test
    fun testFormatJsonIfPossibleInvalidJson() {
        val rawText = "Plain text response"
        val formatted = formatJsonIfPossible(rawText)
        assertEquals("Plain text response", formatted)
    }
}
