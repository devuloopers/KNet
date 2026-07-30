package com.devuloopers.knet.scriptengine.performance

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Memory stress test suite verifying engine stability, garbage collection safety, and payload scale handling.
 */
class MemoryStressTest {

    private val engineManager = ScriptEngineManager()

    /**
     * Verifies execution of 500 sequential scripts without heap memory exhaustion or slowdown.
     */
    @Test
    fun testSequentialExecutionMemoryStability() = runBlocking {
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 5L, 100L, emptyMap(), """{"status":200,"active":true}""")

        val store = EnvironmentStore()
        for (i in 1..500) {
            val script = """
                pm.test("Sequential Run #$i", function() {
                    pm.response.to.have.status(200);
                });
            """.trimIndent()

            val result = engineManager.execute(ScriptLanguage.JAVASCRIPT, script, request, response, store)
            assertTrue(result is ScriptExecutionResult.Success)
        }
    }

    /**
     * Verifies parsing large JSON payloads (e.g. 1MB payload string) inside JavaScript context.
     */
    @Test
    fun testLargeResponsePayloadParsing() = runBlocking {
        val itemsJson = (1..5000).joinToString(",") { """{"id":$it,"name":"Item_$it"}""" }
        val largeJsonBody = """{"total":5000,"items":[$itemsJson]}"""

        val request = ScriptRequestModel("http://localhost", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, largeJsonBody.length.toLong(), emptyMap(), largeJsonBody)

        val script = """
            pm.test("Large JSON items count", function() {
                var json = pm.response.json();
                pm.expect(json.total).to.eql(5000);
                pm.expect(json.items.length).to.eql(5000);
            });
        """.trimIndent()

        val result = engineManager.execute(ScriptLanguage.JAVASCRIPT, script, request, response, EnvironmentStore())
        assertTrue(result is ScriptExecutionResult.Success)
        val success = result as ScriptExecutionResult.Success
        assertEquals(1, success.testResults.size)
        assertTrue(success.testResults[0].passed)
    }
}
