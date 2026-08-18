package com.devuloopers.knet.engine.script.regression

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.engine.script.runtime.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinRegressionTest {

    private val manager = ScriptEngineManager()

    @Test
    fun testKotlinContextDslAndFreshBindings() = runBlocking {
        val req = TestFixtures.createSampleRequest()
        val resp = TestFixtures.createSampleResponse()
        val env = EnvironmentStore()

        val script = """
            test("Context request and response access") {
                response.statusCode == 200 && request.method == "POST"
            }
        """.trimIndent()

        val result1 = manager.execute(ScriptLanguage.KOTLIN, script, req, resp, env)
        assertTrue(result1 is ScriptExecutionResult.Success)
        assertEquals(1, result1.testResults.size)
        assertTrue(result1.testResults[0].passed)

        // Repeat execution to ensure fresh bindings & clean result collector
        val result2 = manager.execute(ScriptLanguage.KOTLIN, script, req, resp, env)
        assertTrue(result2 is ScriptExecutionResult.Success)
        assertEquals(1, result2.testResults.size)
        assertTrue(result2.testResults[0].passed)
    }
}
