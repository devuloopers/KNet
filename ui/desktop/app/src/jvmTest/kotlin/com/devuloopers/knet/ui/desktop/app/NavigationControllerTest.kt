package com.devuloopers.knet.ui.desktop.app

import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationController
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying navigation requests and state transitions via NavigationController.
 */
class NavigationControllerTest {

    @Test
    fun `verify initial destination is default traffic`() {
        val controller = NavigationController()
        assertEquals(DesktopDestination.Traffic, controller.currentDestination.value)
    }

    @Test
    fun `verify initial destination can be configured`() {
        val controller = NavigationController(DesktopDestination.Workspace)
        assertEquals(DesktopDestination.Workspace, controller.currentDestination.value)
    }

    @Test
    fun `verify navigation state updates on navigate`() {
        val controller = NavigationController(DesktopDestination.Traffic)
        controller.navigate(DesktopDestination.Certificate)
        assertEquals(DesktopDestination.Certificate, controller.currentDestination.value)
    }
}
