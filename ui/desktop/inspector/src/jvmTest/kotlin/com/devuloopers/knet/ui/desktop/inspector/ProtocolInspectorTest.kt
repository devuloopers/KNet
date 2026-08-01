package com.devuloopers.knet.ui.desktop.inspector

import com.devuloopers.knet.ui.desktop.inspector.model.InspectorTab
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for Protocol inspection tab in `:ui:desktop:inspector`.
 */
class ProtocolInspectorTest {

    @Test
    fun `PROTOCOL enum value exists`() {
        assertEquals("PROTOCOL", InspectorTab.PROTOCOL.name)
    }
}
