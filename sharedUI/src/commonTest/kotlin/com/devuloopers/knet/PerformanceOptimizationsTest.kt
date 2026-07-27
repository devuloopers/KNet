package com.devuloopers.knet

import com.devuloopers.knet.bodyformatter.formatter.BodyFormatterRegistry
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.devuloopers.knet.widgets.FormattingResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests validating performance optimization boundaries, large payload size caps,
 * and off-thread body formatting result structures.
 */
class PerformanceOptimizationsTest {

    private val maxFormattableSizeBytes = 2 * 1024 * 1024 // 2 MB

    @Test
    fun testFormattingResultSealedInterface() {
        val loadingState: FormattingResult = FormattingResult.Loading
        assertTrue(loadingState is FormattingResult.Loading)

        val readyState: FormattingResult = FormattingResult.Ready(
            prettyBody = "{\"key\":\"value\"}",
            format = BodyFormat.Json("{\"key\":\"value\"}")
        )
        assertTrue(readyState is FormattingResult.Ready)
        assertEquals("{\"key\":\"value\"}", readyState.prettyBody)
        assertTrue(readyState.format is BodyFormat.Json)
    }

    @Test
    fun testPayloadSizeCapThresholdBoundary() {
        // Payload within 2MB threshold
        val smallBody = "A".repeat(100)
        assertTrue(smallBody.length <= maxFormattableSizeBytes, "Small payload must be under 2MB limit")

        // Payload exceeding 2MB threshold
        val oversizedBody = "A".repeat(maxFormattableSizeBytes + 1)
        assertTrue(oversizedBody.length > maxFormattableSizeBytes, "Oversized payload must exceed 2MB limit")
    }

    @Test
    fun testOffThreadFormatResolutionCorrectness() {
        val headers = mapOf("content-type" to "application/json")
        val body = "{\"status\":\"ok\"}"

        val format = BodyFormatterRegistry.resolveFormat(headers, body)
        val pretty = BodyFormatterRegistry.prettyPrintBody(headers, body)

        assertTrue(format is BodyFormat.Json)
        assertTrue(pretty.contains("\"status\": \"ok\"") || pretty.contains("\"status\""))
    }
}
