package com.devuloopers.knet.ui.desktop.workspace

import com.devuloopers.knet.ui.desktop.workspace.model.WorkspaceIntent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for WorkspaceSearch intent handling in `:ui:desktop:workspace`.
 */
class WorkspaceSearchTest {

    @Test
    fun `WorkspaceIntent Search holds query string`() {
        val intent = WorkspaceIntent.Search(query = "users")
        assertEquals("users", intent.query)
    }
}
