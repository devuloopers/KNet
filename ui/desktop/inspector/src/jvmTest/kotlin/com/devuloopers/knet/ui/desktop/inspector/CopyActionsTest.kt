package com.devuloopers.knet.ui.desktop.inspector

import com.devuloopers.knet.ui.desktop.inspector.model.InspectorIntent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for CopyActions in `:ui:desktop:inspector`.
 */
class CopyActionsTest {

    @Test
    fun `InspectorIntent SelectBodyMode holds mode`() {
        val intent = InspectorIntent.SelectBodyMode(mode = "Hex")
        assertEquals("Hex", intent.mode)
    }
}
