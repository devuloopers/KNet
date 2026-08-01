package com.devuloopers.knet.ui.desktop.app

import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import com.devuloopers.knet.ui.desktop.app.window.MainWindowState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests verifying MainWindowState initialization and bounds management.
 */
class MainWindowStateTest {

    @Test
    fun `verify default states and subcomponents are created`() {
        val state = MainWindowState()
        assertNotNull(state.navigationController)
        assertNotNull(state.windowState)
        assertEquals("KNet — Desktop Proxy Studio", state.windowState.title)
        assertEquals(DesktopDestination.Traffic, state.navigationController.currentDestination.value)
    }
}
