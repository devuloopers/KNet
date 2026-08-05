package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.ui.desktop.apistudio.model.ResponsePresentation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for ResponsePresentation formatting in `:ui:desktop:apistudio`.
 */
class ResponsePresentationTest {

    @Test
    fun `ResponsePresentation holds values correctly`() {
        val presentation = ResponsePresentation(
            statusCode = 201,
            statusText = "Created",
            durationMs = 120,
            sizeBytes = 512,
            mimeType = "application/json",
            body = "{\"status\":\"created\"}"
        )

        assertEquals(201, presentation.statusCode)
        assertEquals("Created", presentation.statusText)
        assertEquals(120, presentation.durationMs)
        assertEquals(512, presentation.sizeBytes)
        assertEquals("application/json", presentation.mimeType)
        assertEquals(0, presentation.testResults.size)
        assertEquals(0, presentation.consoleLogs.size)
    }
}
