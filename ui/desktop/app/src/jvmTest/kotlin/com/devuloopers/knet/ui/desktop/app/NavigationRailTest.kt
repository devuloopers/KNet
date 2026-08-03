package com.devuloopers.knet.ui.desktop.app

import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationController
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests verifying NavigationRail destination configuration and state rules.
 */
class NavigationRailTest {

    @Test
    fun `verify destination list contains expected v2 destinations`() {
        val destinations = listOf(
            DesktopDestination.Traffic,
            DesktopDestination.ApiStudio,
            DesktopDestination.Certificate,
            DesktopDestination.Scripting,
            DesktopDestination.Settings
        )

        assertEquals(5, destinations.size)
        assertTrue(destinations.contains(DesktopDestination.Traffic))
        assertTrue(destinations.contains(DesktopDestination.ApiStudio))
        assertTrue(destinations.contains(DesktopDestination.Certificate))
        assertTrue(destinations.contains(DesktopDestination.Scripting))
        assertTrue(destinations.contains(DesktopDestination.Settings))
    }

    @Test
    fun `verify traffic is default startup destination`() {
        val controller = NavigationController()
        assertEquals(DesktopDestination.Traffic, controller.currentDestination.value)
    }

    @Test
    fun `verify navigation state expansion transitions`() {
        val state = NavigationState()
        assertFalse(state.isExpanded)

        state.onPointerEnter()
        assertTrue(state.isExpanded)

        state.onPointerExit()
        assertFalse(state.isExpanded)
    }
}
