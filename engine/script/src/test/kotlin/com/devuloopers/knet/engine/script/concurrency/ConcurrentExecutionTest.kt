package com.devuloopers.knet.engine.script.concurrency

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.engine.script.runtime.ScriptEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrentExecutionTest {

    private val manager = ScriptEngineManager()

    @Test
    fun testParallelScriptExecutions() {
        runBlocking {
            val jobs = (1..20).map { index ->
                async(Dispatchers.Default) {
                    val req = TestFixtures.createSampleRequest()
                    val resp = TestFixtures.createSampleResponse()
                    val env = EnvironmentStore()

                    val jsScript = "pm.environment.set('thread', '$index');"
                    val result = manager.execute(ScriptLanguage.JAVASCRIPT, jsScript, req, resp, env)

                    assertTrue(result is ScriptExecutionResult.Success)
                    assertEquals(index.toString(), env["thread"])
                }
            }
            jobs.awaitAll()
        }
    }
}
