package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.ui.desktop.apistudio.model.ResponseInspectorState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the API Studio response-inspector projection.
 */
class ResponseInspectorStateTest {

    @Test
    fun `ResponseInspectorState holds values correctly`() {
        val presentation = ResponseInspectorState(
            statusCode = 201,
            statusText = "Created",
            durationMs = 120,
            sizeBytes = 512,
            headers = mapOf("Content-Type" to "application/json"),
            responseBody = "{\"status\":\"created\"}",
        )

        assertEquals(201, presentation.statusCode)
        assertEquals("Created", presentation.statusText)
        assertEquals(120, presentation.durationMs)
        assertEquals(512, presentation.sizeBytes)
        assertEquals("application/json", presentation.headers["Content-Type"])
        assertEquals("{\"status\":\"created\"}", presentation.responseBody)
        assertEquals(0, presentation.testResults.size)
        assertEquals(0, presentation.consoleLogs.size)
    }
}
