package com.devuloopers.knet.engine.script.api

import com.devuloopers.knet.scripting.model.ScriptAssertion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptModelsTest {

    @Test
    fun testScriptAssertionAndExecutionResult() {
        val testResult = ScriptAssertion("Status is 200", true, null, 15L)
        assertEquals("Status is 200", testResult.name)
        assertTrue(testResult.passed)

        val success = ScriptExecutionResult.Success(
            request = ScriptRequestModel("https://x.com", "GET", mutableMapOf(), mutableMapOf(), ""),
            testResults = listOf(testResult),
            environmentUpdates = emptyMap(),
            logs = listOf("log line")
        )
        assertEquals(1, success.testResults.size)
        assertEquals(1, success.logs.size)

        val error = ScriptExecutionResult.Error("Compilation error", 10, 5)
        assertEquals(10, error.line)
        assertEquals(5, error.column)
    }
}
