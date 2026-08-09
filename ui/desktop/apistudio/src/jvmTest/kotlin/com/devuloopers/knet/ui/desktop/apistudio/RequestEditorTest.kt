package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for RequestEditorState in `:ui:desktop:apistudio`.
 */
class RequestEditorTest {

    @Test
    fun `RequestEditorState default values are set`() {
        val state = RequestEditorState()
        assertEquals("", state.url)
        assertEquals("GET", state.method)
        assertEquals("No Auth", state.authType)
        assertEquals("None", state.bodyType)
    }
}
