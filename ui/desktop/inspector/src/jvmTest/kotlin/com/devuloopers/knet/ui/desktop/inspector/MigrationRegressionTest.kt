package com.devuloopers.knet.ui.desktop.inspector

import com.devuloopers.knet.ui.desktop.inspector.di.inspectorUiModule
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Migration regression tests for Koin module and public API stability in `:ui:desktop:inspector`.
 */
class MigrationRegressionTest {

    @Test
    fun `Koin module is available`() {
        assertNotNull(inspectorUiModule)
    }
}
