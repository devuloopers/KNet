package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

class FormatterIntegrationTest {

    @Test
    fun testEndToEndFormattingPipeline() {
        val format = BodyFormatterRegistry.resolveFormat(mapOf("content-type" to "application/json"), TestFixtures.SAMPLE_JSON)
        assertTrue(format is BodyFormat.Json)

        val prettyText = BodyFormatterRegistry.prettyPrintBody(mapOf("content-type" to "application/json"), TestFixtures.SAMPLE_JSON)
        assertTrue(prettyText.contains("KNet"))
    }
}
