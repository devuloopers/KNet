package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JsonBodyFormatterTest {
    private val formatter = JsonBodyFormatter()

    @Test
    fun testJsonMatchingAndFormatting() {
        val rawJson = "{\"name\":\"KNet\",\"version\":1}"
        assertTrue(formatter.matches(emptyMap(), rawJson))

        val result = formatter.format(emptyMap(), rawJson)
        assertTrue(result is BodyFormat.Json)
        assertTrue(result.formattedText.contains("\"name\""))
        assertTrue(result.formattedText.contains("KNet"))
    }

    @Test
    fun testGoogleXssiPrefixStripping() {
        val googleXssiPayload = ")]}'\n[\"\",[\"bitsat iteration 4\",\"thiago almada\"]]"
        assertTrue(formatter.matches(mapOf("content-type" to "application/x-javascript"), googleXssiPayload))

        val result = formatter.format(mapOf("content-type" to "application/x-javascript"), googleXssiPayload)
        assertTrue(result is BodyFormat.Json)
        assertTrue(result.formattedText.startsWith("["))
        assertTrue(!result.formattedText.startsWith(")"))
    }

    @Test
    fun newlineDelimitedJsonUsesFormattedIndependentFrames() {
        val ndJson = """
            {"id":1,"name":"first"}
            {"id":2,"name":"second"}
        """.trimIndent()

        val result = assertIs<BodyFormat.JsonStream>(formatter.format(emptyMap(), ndJson))

        assertEquals(2, result.frames.size)
        assertTrue(result.frames.all { it.lines().size > 1 })
    }

    @Test
    fun explicitNdJsonMediaTypeRetainsStreamShapeWithOneRecord() {
        val result = assertIs<BodyFormat.JsonStream>(
            formatter.format(
                mapOf("content-type" to "application/x-ndjson; charset=utf-8"),
                "{\"id\":1}"
            )
        )

        assertEquals(1, result.frames.size)
    }

    @Test
    fun multilineJsonArrayRemainsOneJsonDocument() {
        val json = """
            [
              {"id": 1},
              {"id": 2}
            ]
        """.trimIndent()

        assertIs<BodyFormat.Json>(formatter.format(emptyMap(), json))
    }

    @Test
    fun unrelatedMultilineTextIsNotImplicitlyClassifiedAsNdJson() {
        val text = "first line\nsecond line"

        assertFalse(formatter.matches(emptyMap(), text))
    }

    @Test
    fun oneInvalidRecordDoesNotBecomeAnImplicitJsonStream() {
        val payload = "{\"id\":1}\nnot-json"

        assertIs<BodyFormat.Json>(formatter.format(emptyMap(), payload))
    }
}
