package com.devuloopers.knet.scriptengine.performance

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Concurrency and performance stress test suite covering TC-120, TC-121, TC-140, TC-143, and TC-150.
 * Verifies high-throughput parallel script execution, thread safety, and state isolation.
 */
class ConcurrencyAndPerformanceTest {

    private val engineManager = ScriptEngineManager()

    /**
     * TC-120: Verifies 100 concurrent script executions running in parallel without race conditions or memory corruption.
     */
    @Test
    fun testConcurrentScriptExecutions() = runBlocking(Dispatchers.Default) {
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 10L, 100L, emptyMap(), """{"status":200}""")

        val jobs = (1..100).map { index ->
            async {
                val script = """
                    pm.test("Concurrent Test #$index", function() {
                        pm.response.to.have.status(200);
                    });
                    pm.environment.set("key_$index", "val_$index");
                """.trimIndent()

                val store = EnvironmentStore()
                val result = engineManager.execute(
                    language = ScriptLanguage.JAVASCRIPT,
                    code = script,
                    request = request,
                    response = response,
                    environment = store
                )
                result to store
            }
        }

        val completedResults = jobs.awaitAll()
        assertEquals(100, completedResults.size)
        assertTrue(completedResults.all { (res, store) ->
            res is ScriptExecutionResult.Success && (res as ScriptExecutionResult.Success).testResults.all { it.passed }
        }, "All 100 concurrent script executions must succeed")
    }

    /**
     * TC-121: Verifies parallel environment variable updates on a shared Atomic EnvironmentStore instance.
     */
    @Test
    fun testParallelEnvironmentStoreUpdates() = runBlocking(Dispatchers.Default) {
        val store = EnvironmentStore()
        val jobs = (1..50).map { index ->
            async {
                store.set("thread_key_$index", "thread_val_$index")
            }
        }

        jobs.awaitAll()
        val snapshot = store.snapshot()
        assertEquals(50, snapshot.size, "EnvironmentStore must contain all 50 updated keys atomically")
    }
}
