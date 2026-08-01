package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.ui.desktop.apistudio.model.ApiStudioState
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for ApiStudioState in `:ui:desktop:apistudio`.
 */
class ApiStudioViewModelTest {

    @Test
    fun `ApiStudioState default state is initialized correctly`() {
        val state = ApiStudioState()
        assertEquals(1, state.tabs.size)
        assertEquals("tab_1", state.activeTabId)
        assertEquals(ExecutionState.IDLE, state.executionState)
        assertNull(state.responsePresentation)
    }
}
