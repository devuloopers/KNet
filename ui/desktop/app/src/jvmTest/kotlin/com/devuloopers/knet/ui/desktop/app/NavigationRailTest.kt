package com.devuloopers.knet.ui.desktop.app

import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationController
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationState
import com.devuloopers.knet.ui.desktop.app.navigation.primaryNavigationItems
import com.devuloopers.knet.ui.desktop.app.navigation.setupNavigationItems
import com.devuloopers.knet.ui.desktop.app.navigation.utilityNavigationItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests verifying NavigationRail destination configuration and state rules.
 */
class NavigationRailTest {

    @Test
    fun `verify destination list contains expected desktop destinations`() {
        val primaryDestinations = primaryNavigationItems.map { it.destination }
        val setupDestinations = setupNavigationItems.map { it.destination }
        val utilityDestinations = utilityNavigationItems.map { it.destination }
        val destinations = primaryDestinations + setupDestinations + utilityDestinations

        assertEquals(6, destinations.size)
        assertTrue(destinations.contains(DesktopDestination.Traffic))
        assertTrue(destinations.contains(DesktopDestination.ConnectDevice))
        assertTrue(destinations.contains(DesktopDestination.ApiStudio))
        assertTrue(destinations.contains(DesktopDestination.Breakpoints))
        assertTrue(destinations.contains(DesktopDestination.Certificate))
        assertTrue(destinations.contains(DesktopDestination.Settings))
        assertEquals(
            listOf(DesktopDestination.ConnectDevice, DesktopDestination.Certificate),
            setupDestinations
        )
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
