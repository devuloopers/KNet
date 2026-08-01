package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.desktop.scripting.model.ScriptExecutionState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying ScriptExecutionState enum keys.
 */
class ScriptExecutionStateTest {

    @Test
    fun `ScriptExecutionState enum values exist`() {
        assertEquals("RUNNING", ScriptExecutionState.RUNNING.name)
        assertEquals("SUCCESS", ScriptExecutionState.SUCCESS.name)
    }
}
