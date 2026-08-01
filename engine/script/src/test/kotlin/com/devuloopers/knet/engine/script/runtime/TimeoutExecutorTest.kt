package com.devuloopers.knet.engine.script.runtime

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class TimeoutExecutorTest {

    @Test
    fun testSuccessfulExecutionAndTimeout() = runBlocking {
        val successResult = TimeoutExecutor.executeWithTimeout(500L) {
            ScriptExecutionResult.Success(
                request = TestFixtures.createSampleRequest(),
                testResults = emptyList(),
                environmentUpdates = emptyMap(),
                logs = emptyList()
            )
        }
        assertTrue(successResult is ScriptExecutionResult.Success)

        var callbackCalled = false
        val timeoutResult = TimeoutExecutor.executeWithTimeout(100L, onTimeout = { callbackCalled = true }) {
            delay(500L.milliseconds)
            ScriptExecutionResult.Success(
                request = TestFixtures.createSampleRequest(),
                testResults = emptyList(),
                environmentUpdates = emptyMap(),
                logs = emptyList()
            )
        }
        assertTrue(timeoutResult is ScriptExecutionResult.Error)
        assertTrue(callbackCalled)
    }
}
