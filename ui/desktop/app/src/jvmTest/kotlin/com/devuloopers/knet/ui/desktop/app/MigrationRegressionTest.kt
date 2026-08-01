package com.devuloopers.knet.ui.desktop.app

import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationController
import com.devuloopers.knet.ui.desktop.app.window.MainWindowState
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Migration regression tests for Explicit Navigation API stability in `:ui:desktop:app`.
 */
class MigrationRegressionTest {

    @Test
    fun `Explicit navigation classes exist and are available`() {
        assertNotNull(DesktopDestination::class)
        assertNotNull(NavigationController::class)
        assertNotNull(MainWindowState::class)
    }
}
