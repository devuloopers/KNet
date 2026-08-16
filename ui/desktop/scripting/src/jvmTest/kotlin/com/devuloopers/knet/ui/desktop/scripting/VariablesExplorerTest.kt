package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.desktop.scripting.model.ExecutionContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying VariablesExplorer context properties.
 */
class VariablesExplorerTest {

    @Test
    fun `ExecutionContext properties match environment variables`() {
        val vars = listOf(KeyValueEntry(id = "1", key = "baseUrl", value = "https://api.github.com"))
        val context = ExecutionContext(environment = vars)
        assertEquals(vars, context.environment)
    }
}


