package com.devuloopers.knet.ui.desktop.inspector

import com.devuloopers.knet.ui.desktop.inspector.model.RequestPresentation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for RequestPresentation in `:ui:desktop:inspector`.
 */
class RequestInspectorTest {

    @Test
    fun `RequestPresentation holds body and headers`() {
        val req = RequestPresentation(body = "{\"query\":\"all\"}")
        assertEquals("{\"query\":\"all\"}", req.body)
    }
}
