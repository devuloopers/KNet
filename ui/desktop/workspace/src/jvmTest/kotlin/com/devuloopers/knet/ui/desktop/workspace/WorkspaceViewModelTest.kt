package com.devuloopers.knet.ui.desktop.workspace

import com.devuloopers.knet.ui.desktop.workspace.model.ExplorerType
import com.devuloopers.knet.ui.desktop.workspace.model.WorkspaceLayoutData
import com.devuloopers.knet.ui.desktop.workspace.model.WorkspaceSelection
import com.devuloopers.knet.ui.desktop.workspace.model.WorkspaceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for Workspace state models in `:ui:desktop:workspace`.
 */
class WorkspaceViewModelTest {

    @Test
    fun `WorkspaceState holds parameters correctly`() {
        val state = WorkspaceState.Success(
            activeExplorer = ExplorerType.ENVIRONMENTS,
            searchQuery = "auth",
            selection = WorkspaceSelection(id = "1", type = "collection", name = "Auth API"),
            layout = WorkspaceLayoutData(explorerWidthDp = 280f)
        )

        assertEquals(ExplorerType.ENVIRONMENTS, state.activeExplorer)
        assertEquals("auth", state.searchQuery)
        assertNotNull(state.selection)
        assertEquals("Auth API", state.selection.name)
        assertEquals(280f, state.layout.explorerWidthDp)
    }
}
