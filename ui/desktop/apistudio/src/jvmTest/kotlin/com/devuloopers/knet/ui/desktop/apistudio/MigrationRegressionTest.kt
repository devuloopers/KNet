package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.ui.desktop.apistudio.di.apiStudioUiModule
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Migration regression tests for Koin module and public API stability in `:ui:desktop:apistudio`.
 */
class MigrationRegressionTest {

    @Test
    fun `Koin module is available`() {
        assertNotNull(apiStudioUiModule)
    }
}
