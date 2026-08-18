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

class EnvironmentPropagationIntegrationTest {

    private val manager = ScriptEngineManager()

    @Test
    fun testEnvironmentVariablesPropagateAcrossScriptRuns() = runBlocking {
        val req = TestFixtures.createSampleRequest()
        val env = EnvironmentStore(mapOf("initialKey" to "initialVal"))

        val script1 = "pm.environment.set('step1', 'done');"
        manager.execute(ScriptLanguage.JAVASCRIPT, script1, req, null, env)

        assertEquals("done", env["step1"])

        val script2 = """
            test("Step 1 propagated") {
                env["step1"] == "done"
            }
        """.trimIndent()
        val result2 = manager.execute(ScriptLanguage.KOTLIN, script2, req, null, env)

        assertTrue(result2 is ScriptExecutionResult.Success)
        assertEquals(1, result2.testResults.size)
        assertTrue(result2.testResults[0].passed)
    }
}
