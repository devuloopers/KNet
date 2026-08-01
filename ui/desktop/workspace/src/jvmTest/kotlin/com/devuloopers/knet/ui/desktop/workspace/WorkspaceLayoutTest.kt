package com.devuloopers.knet.ui.desktop.workspace

import com.devuloopers.knet.ui.desktop.workspace.model.WorkspaceLayoutData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for WorkspaceLayoutData configuration in `:ui:desktop:workspace`.
 */
class WorkspaceLayoutTest {

    @Test
    fun `WorkspaceLayoutData default parameters are set`() {
        val layout = WorkspaceLayoutData()
        assertEquals(260f, layout.explorerWidthDp)
        assertEquals(340f, layout.sidebarWidthDp)
        assertEquals(200f, layout.bottomTrayHeightDp)
    }
}
