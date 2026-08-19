package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BodyFormatterRegistryTest {

    @Test
    fun testStage1MimeTypeLookup() {
        val formatJson = BodyFormatterRegistry.resolveFormat(mapOf("content-type" to "application/json"), TestFixtures.SAMPLE_JSON)
        assertTrue(formatJson is BodyFormat.Json)

        val formatXml = BodyFormatterRegistry.resolveFormat(mapOf("content-type" to "application/xml"), TestFixtures.SAMPLE_XML)
        assertTrue(formatXml is BodyFormat.Xml)

        val formatFormData = BodyFormatterRegistry.resolveFormat(mapOf("content-type" to "application/x-www-form-urlencoded"), TestFixtures.SAMPLE_FORM_DATA)
        assertTrue(formatFormData is BodyFormat.FormData)
    }

    @Test
    fun testStage2StructuralFallback() {
        val formatJson = BodyFormatterRegistry.resolveFormat(emptyMap(), TestFixtures.SAMPLE_JSON)
        assertTrue(formatJson is BodyFormat.Json)

        val formatXml = BodyFormatterRegistry.resolveFormat(emptyMap(), TestFixtures.SAMPLE_XML)
        assertTrue(formatXml is BodyFormat.Xml)
    }

    @Test
    fun testPrettyPrintBody() {
        val printed = BodyFormatterRegistry.prettyPrintBody(mapOf("content-type" to "application/json"), TestFixtures.SAMPLE_JSON)
        assertTrue(printed.contains("\"name\""))
        assertTrue(printed.contains("KNet"))
    }

    @Test
    fun testNdJsonAliasesReuseTheJsonStreamShape() {
        val payload = "{\"id\":1}\n{\"id\":2}"
        val mediaTypes = listOf(
            "application/x-ndjson",
            "application/ndjson",
            "application/jsonl",
            "application/x-jsonlines"
        )

        mediaTypes.forEach { mediaType ->
            val format = BodyFormatterRegistry.resolveFormat(
                mapOf("content-type" to mediaType),
                payload
            )

            assertEquals(2, assertIs<BodyFormat.JsonStream>(format).frames.size)
        }
    }
}
