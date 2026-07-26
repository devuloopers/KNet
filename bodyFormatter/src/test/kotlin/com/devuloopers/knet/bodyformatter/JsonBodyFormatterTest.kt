package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.JsonBodyFormatter
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
