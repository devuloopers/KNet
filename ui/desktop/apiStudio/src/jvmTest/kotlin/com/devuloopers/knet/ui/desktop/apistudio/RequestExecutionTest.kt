package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for ExecutionState enum values in `:ui:desktop:apiStudio`.
 */
class RequestExecutionTest {

    @Test
    fun `ExecutionState values exist`() {
        val states = ExecutionState.entries
        assertEquals(4, states.size)
    }
}
