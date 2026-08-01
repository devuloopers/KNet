package com.devuloopers.knet.engine.script.javascript

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraalJsScriptEngineTest {

    private val engine = GraalJsScriptEngine()

    @Test
    fun testJsExecutionAssertionsAndEnv() = runBlocking {
        val req = TestFixtures.createSampleRequest()
        val resp = TestFixtures.createSampleResponse()
        val env = EnvironmentStore()

        val jsCode = """
            console.log("Testing GraalJS");
            pm.environment.set("token", "secret_123");
            pm.test("Status code is 200", function() {
                pm.response.to.have.status(200);
            });
            pm.test("Check JSON body", function() {
                var json = pm.response.json();
                pm.expect(json.success).to.eql(true);
            });
        """.trimIndent()

        val result = engine.execute(jsCode, req, resp, env)
        assertTrue(result is ScriptExecutionResult.Success)
        assertEquals("secret_123", env["token"])
        assertEquals(2, result.testResults.size)
        assertTrue(result.testResults.all { it.passed })
        assertTrue(result.logs.contains("Testing GraalJS"))
    }
}
