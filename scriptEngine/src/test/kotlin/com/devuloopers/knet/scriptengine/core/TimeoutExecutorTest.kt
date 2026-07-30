package com.devuloopers.knet.scriptengine.core

import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit test suite for [TimeoutExecutor] covering TC-090 and TC-091.
 * Verifies execution timeout protection, execution cancellation, and completion within limits.
 */
class TimeoutExecutorTest {

    /**
     * TC-090: Verifies that infinite or stalling execution blocks are cancelled on timeout.
     */
    @Test
    fun testTimeoutCancellation() = runBlocking {
        var cleanupCalled = false
        val result = TimeoutExecutor.executeWithTimeout(
            timeoutMs = 100L,
            onTimeout = { cleanupCalled = true }
        ) {
            delay(500L) // Stalling block exceeding 100ms timeout
            ScriptExecutionResult.Success(
                request = ScriptRequestModel("", "", mutableMapOf(), mutableMapOf(), ""),
                testResults = emptyList(),
                environmentUpdates = emptyMap(),
                logs = emptyList()
            )
        }

        assertTrue(cleanupCalled, "onTimeout cleanup should be invoked")
        assertTrue(result is ScriptExecutionResult.Error, "Result should be an Error model on timeout")
        val error = result as ScriptExecutionResult.Error
        assertTrue(error.message.contains("timed out"), "Message should indicate timeout")
    }

    /**
     * TC-091: Verifies that executions completing within timeout limits succeed normally.
     */
    @Test
    fun testNormalExecutionWithinTimeout() = runBlocking {
        val request = ScriptRequestModel("http://localhost", "GET", mutableMapOf(), mutableMapOf(), "")
        val result = TimeoutExecutor.executeWithTimeout(timeoutMs = 1000L) {
            delay(10L)
            ScriptExecutionResult.Success(
                request = request,
                testResults = emptyList(),
                environmentUpdates = emptyMap(),
                logs = emptyList()
            )
        }

        assertTrue(result is ScriptExecutionResult.Success, "Result should succeed within timeout limit")
        assertEquals(request, (result as ScriptExecutionResult.Success).request)
    }
}
