package com.devuloopers.knet.engine.script.kotlin

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinScriptEngineTest {

    private val engine = KotlinScriptEngine()

    @Test
    fun testKotlinScriptExecution() = runBlocking {
        val req = TestFixtures.createSampleRequest()
        val resp = TestFixtures.createSampleResponse()
        val env = EnvironmentStore()

        val script = """
            env["session"] = "active"
            test("Status is 200") {
                response.statusCode == 200
            }
        """.trimIndent()

        val result = engine.execute(script, req, resp, env)
        assertTrue(result is ScriptExecutionResult.Success)
        assertEquals("active", env["session"])
        assertEquals(1, result.testResults.size)
        assertTrue(result.testResults[0].passed)
    }
}
