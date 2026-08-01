package com.devuloopers.knet.ui.desktop.workspace

import com.devuloopers.knet.ui.desktop.workspace.explorer.TreeNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for TreeNode data structures in `:ui:desktop:workspace`.
 */
class ExplorerTreeTest {

    @Test
    fun `TreeNode structure holds label and children`() {
        val child = TreeNode<String>(id = "child", label = "GET /user")
        val root = TreeNode(id = "root", label = "Users Collection", children = listOf(child))

        assertEquals("root", root.id)
        assertEquals(1, root.children.size)
        assertEquals("child", root.children[0].id)
    }
}
