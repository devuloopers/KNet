package com.devuloopers.knet.engine.script.integration

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.engine.script.runtime.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptExecutionIntegrationTest {

    private val manager = ScriptEngineManager()

    @Test
    fun testFullScriptPipelineIntegration() = runBlocking {
        val req = TestFixtures.createSampleRequest()
        val resp = TestFixtures.createSampleResponse()
        val env = EnvironmentStore()

        val preScript = "pm.environment.set('req_id', '12345');"
        val preResult = manager.execute(ScriptLanguage.JAVASCRIPT, preScript, req, null, env)
        assertTrue(preResult is ScriptExecutionResult.Success)
        assertEquals("12345", env["req_id"])

        val testScript = """
            test("Response status is 200") {
                response.statusCode == 200
            }
        """.trimIndent()
        val testResult = manager.execute(ScriptLanguage.KOTLIN, testScript, req, resp, env)
        assertTrue(testResult is ScriptExecutionResult.Success)
        assertEquals(1, testResult.testResults.size)
        assertTrue(testResult.testResults[0].passed)
    }
}
