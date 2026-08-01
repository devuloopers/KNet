package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.ui.desktop.traffic.model.TrafficState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for TrafficState in `:ui:desktop:traffic`.
 */
class TrafficViewModelTest {

    @Test
    fun `TrafficState default values are set`() {
        val state = TrafficState()
        assertTrue(state.transactions.isEmpty())
        assertFalse(state.isPaused)
        assertTrue(state.autoScroll)
    }
}
