package com.devuloopers.knet.engine.script.runtime

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptLanguage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScriptEngineManagerTest {

    private val manager = ScriptEngineManager()

    @Test
    fun testManagerEngineRegistrationAndRouting() {
        runBlocking {
            val jsEngine = manager.getEngine(ScriptLanguage.JAVASCRIPT)
            val kotlinEngine = manager.getEngine(ScriptLanguage.KOTLIN)

            assertNotNull(jsEngine)
            assertNotNull(kotlinEngine)

            val req = TestFixtures.createSampleRequest()
            val env = EnvironmentStore()

            val jsResult = manager.execute(
                language = ScriptLanguage.JAVASCRIPT,
                code = "console.log('JS execution test');",
                request = req,
                response = null,
                environment = env
            )
            assertTrue(jsResult is ScriptExecutionResult.Success)

            val ktResult = manager.execute(
                language = ScriptLanguage.KOTLIN,
                code = "env[\"test\"] = \"ok\"",
                request = req,
                response = null,
                environment = env
            )
            assertTrue(ktResult is ScriptExecutionResult.Success)
        }
    }
}
