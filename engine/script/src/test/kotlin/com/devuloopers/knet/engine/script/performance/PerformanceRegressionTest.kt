package com.devuloopers.knet.engine.script.performance

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptLanguage
import com.devuloopers.knet.engine.script.runtime.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceRegressionTest {

    private val manager = ScriptEngineManager()

    @Test
    fun testRepeatedScriptExecutionsPerformance() = runBlocking {
        val req = TestFixtures.createSampleRequest()
        val resp = TestFixtures.createSampleResponse()
        val env = EnvironmentStore()

        val jsScript = "pm.test('Pass', function() { pm.response.to.have.status(200); });"

        repeat(50) {
            val result = manager.execute(ScriptLanguage.JAVASCRIPT, jsScript, req, resp, env)
            assertTrue(result is ScriptExecutionResult.Success)
        }
    }
}
