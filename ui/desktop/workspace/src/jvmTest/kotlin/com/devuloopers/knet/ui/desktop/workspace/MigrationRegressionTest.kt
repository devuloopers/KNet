package com.devuloopers.knet.ui.desktop.workspace

import com.devuloopers.knet.ui.desktop.workspace.di.workspaceUiModule
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Migration regression tests for public symbols and Koin module in `:ui:desktop:workspace`.
 */
class MigrationRegressionTest {

    @Test
    fun `Workspace Koin module exists`() {
        assertNotNull(workspaceUiModule)
    }
}
