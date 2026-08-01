package com.devuloopers.knet.ui.desktop.app

import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Unit tests verifying that the DesktopDestination sealed interface is correctly defined and all objects can be instantiated.
 */
class DesktopDestinationTest {

    @Test
    fun `verify all desktop destinations exist`() {
        assertNotNull(DesktopDestination.Workspace)
        assertNotNull(DesktopDestination.Traffic)
        assertNotNull(DesktopDestination.Inspector)
        assertNotNull(DesktopDestination.ApiStudio)
        assertNotNull(DesktopDestination.Scripting)
        assertNotNull(DesktopDestination.Certificate)
        assertNotNull(DesktopDestination.Settings)
    }
}
