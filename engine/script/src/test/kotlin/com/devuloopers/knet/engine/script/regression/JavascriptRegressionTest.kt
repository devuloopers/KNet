package com.devuloopers.knet.engine.script.regression

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptLanguage
import com.devuloopers.knet.engine.script.runtime.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavascriptRegressionTest {

    private val manager = ScriptEngineManager()

    @Test
    fun testJavascriptJsonEscapingAndPmPolyfills() = runBlocking {
        val req = TestFixtures.createSampleRequest()
        val resp = TestFixtures.createSampleResponse()
        val env = EnvironmentStore()

        val dollarSign = "$"
        val script = """
            pm.test("Status code is 200", function() {
                pm.response.to.have.status(200);
            });
            pm.environment.set("special_char", "quotes\"and${dollarSign}dollars");
        """.trimIndent()

        val result = manager.execute(ScriptLanguage.JAVASCRIPT, script, req, resp, env)
        assertTrue(result is ScriptExecutionResult.Success)
        assertEquals($$"quotes\"and$dollars", env["special_char"])
        assertEquals(1, result.testResults.size)
        assertTrue(result.testResults[0].passed)
    }
}
