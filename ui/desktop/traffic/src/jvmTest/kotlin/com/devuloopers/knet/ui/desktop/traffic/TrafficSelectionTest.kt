package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.ui.desktop.traffic.model.TrafficSelection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for TrafficSelection in `:ui:desktop:traffic`.
 */
class TrafficSelectionTest {

    @Test
    fun `TrafficSelection supports multi-select ids`() {
        val selection = TrafficSelection(selectedIds = setOf("tx_1", "tx_2"), primarySelectedId = "tx_1")
        assertEquals(2, selection.selectedIds.size)
        assertEquals("tx_1", selection.primarySelectedId)
    }
}
