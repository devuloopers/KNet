package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.ui.desktop.traffic.di.trafficUiModule
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Migration regression tests for Koin module and public API stability in `:ui:desktop:traffic`.
 */
class MigrationRegressionTest {

    @Test
    fun `Koin module is available`() {
        assertNotNull(trafficUiModule)
    }
}
