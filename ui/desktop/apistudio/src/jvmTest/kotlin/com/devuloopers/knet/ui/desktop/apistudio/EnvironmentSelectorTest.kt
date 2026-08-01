package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.ui.desktop.apistudio.model.ApiStudioState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for Environment selection state in `:ui:desktop:apistudio`.
 */
class EnvironmentSelectorTest {

    @Test
    fun `ApiStudioState manages environment selection`() {
        val state = ApiStudioState(selectedEnvironment = "Staging")
        assertEquals("Staging", state.selectedEnvironment)
    }
}
