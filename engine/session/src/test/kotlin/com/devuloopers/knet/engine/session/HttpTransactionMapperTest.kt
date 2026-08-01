package com.devuloopers.knet.engine.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpTransactionMapperTest {

    @Test
    fun testHeaderSerializationAndDeserialization() {
        val originalHeaders = listOf(
            "User-Agent" to "KNet/1.0",
            "Authorization" to "Bearer token_abc123",
            "Content-Type" to "application/json"
        )

        val json = HttpTransactionMapper.serializeHeaders(originalHeaders)
        val deserialized = HttpTransactionMapper.deserializeHeaders(json)

        assertEquals(3, deserialized.size)
        assertEquals("User-Agent", deserialized[0].first)
        assertEquals("KNet/1.0", deserialized[0].second)
        assertEquals("Authorization", deserialized[1].first)
        assertEquals("Bearer token_abc123", deserialized[1].second)
    }

    @Test
    fun testEmptyHeaderSerialization() {
        val json = HttpTransactionMapper.serializeHeaders(emptyList())
        assertEquals("[]", json)
        assertTrue(HttpTransactionMapper.deserializeHeaders("[]").isEmpty())
    }

    @Test
    fun testHeaderRegexMatchingWithSpecialRegexCharacters() {
        val complexHeaders = listOf(
            "X-Regex-Header" to ".*+?^$\\{}()|[]",
            "X-Json-Payload-Header" to """{"key":"value","items":[1,2,3]}""",
            "X-Escaped-Quotes" to "Line 1 \"quoted\"\nLine 2"
        )

        val json = HttpTransactionMapper.serializeHeaders(complexHeaders)
        val deserialized = HttpTransactionMapper.deserializeHeaders(json)

        assertEquals(3, deserialized.size)
        assertEquals("X-Regex-Header", deserialized[0].first)
        assertEquals(".*+?^$\\{}()|[]", deserialized[0].second)

        assertEquals("X-Json-Payload-Header", deserialized[1].first)
        assertEquals("""{"key":"value","items":[1,2,3]}""", deserialized[1].second)

        assertEquals("X-Escaped-Quotes", deserialized[2].first)
        assertEquals("Line 1 \"quoted\"\nLine 2", deserialized[2].second)
    }

    @Test
    fun testHeaderRegexMatchingWithMalformedOrEmptyInput() {
        assertTrue(HttpTransactionMapper.deserializeHeaders("").isEmpty())
        assertTrue(HttpTransactionMapper.deserializeHeaders("invalid_json_string").isEmpty())
        assertTrue(HttpTransactionMapper.deserializeHeaders("{}").isEmpty())
    }
}
