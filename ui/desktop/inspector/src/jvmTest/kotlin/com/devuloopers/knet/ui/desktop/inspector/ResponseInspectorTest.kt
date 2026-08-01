package com.devuloopers.knet.ui.desktop.inspector

import com.devuloopers.knet.ui.desktop.inspector.model.ResponsePresentation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for ResponsePresentation in `:ui:desktop:inspector`.
 */
class ResponseInspectorTest {

    @Test
    fun `ResponsePresentation holds statusCode and body`() {
        val res = ResponsePresentation(statusCode = 404, statusText = "Not Found")
        assertEquals(404, res.statusCode)
        assertEquals("Not Found", res.statusText)
    }
}
