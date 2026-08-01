package com.devuloopers.knet.ui.desktop.inspector

import com.devuloopers.knet.ui.desktop.inspector.model.InspectorTab
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for Timing tab enum values in `:ui:desktop:inspector`.
 */
class TimingInspectorTest {

    @Test
    fun `TIMING enum value exists`() {
        assertEquals("TIMING", InspectorTab.TIMING.name)
    }
}
