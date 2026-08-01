package com.devuloopers.knet.apps.desktop

import com.devuloopers.knet.apps.desktop.bootstrap.DesktopBootstrap
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Migration regression test ensuring desktop launcher objects exist under [com.devuloopers.knet.apps.desktop].
 */
class MigrationRegressionTest {

    @Test
    fun testDesktopBootstrapObjectExists() {
        assertNotNull(DesktopBootstrap, "DesktopBootstrap orchestrator object must exist")
    }
}
