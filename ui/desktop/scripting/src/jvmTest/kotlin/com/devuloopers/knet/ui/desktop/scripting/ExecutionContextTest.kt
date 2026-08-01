package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.core.table.KeyValuePair
import com.devuloopers.knet.ui.desktop.scripting.model.ExecutionContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying ExecutionContext mappings.
 */
class ExecutionContextTest {

    @Test
    fun `ExecutionContext attributes match headers`() {
        val requests = listOf(KeyValuePair("Content-Type", "application/json"))
        val context = ExecutionContext(requests = requests)
        assertEquals(requests, context.requests)
    }
}
