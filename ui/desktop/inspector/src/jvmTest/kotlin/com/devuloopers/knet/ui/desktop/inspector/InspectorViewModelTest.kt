package com.devuloopers.knet.ui.desktop.inspector

import com.devuloopers.knet.ui.desktop.inspector.model.InspectorState
import com.devuloopers.knet.ui.desktop.inspector.model.InspectorTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for InspectorState in `:ui:desktop:inspector`.
 */
class InspectorViewModelTest {

    @Test
    fun `InspectorState default values are set`() {
        val state = InspectorState()
        assertEquals(InspectorTab.OVERVIEW, state.activeTab)
        assertNull(state.overview)
        assertEquals("Pretty", state.bodyMode)
    }
}
