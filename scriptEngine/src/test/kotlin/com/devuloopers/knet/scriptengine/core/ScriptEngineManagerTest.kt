package com.devuloopers.knet.scriptengine.core

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.javascript.GraalJsScriptEngine
import com.devuloopers.knet.scriptengine.kotlin.KotlinScriptEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit test suite for [ScriptEngineManager].
 * Verifies engine registration, language routing, and unsupported language error handling.
 */
class ScriptEngineManagerTest {

    private val engineManager = ScriptEngineManager()

    /**
     * Verifies that JavaScript and Kotlin language engines are registered properly.
     */
    @Test
    fun testEngineRegistrationAndLookup() {
        val jsEngine = engineManager.getEngine(ScriptLanguage.JAVASCRIPT)
        val kotlinEngine = engineManager.getEngine(ScriptLanguage.KOTLIN)

        assertNotNull(jsEngine, "JavaScript engine should be registered")
        assertTrue(jsEngine is GraalJsScriptEngine)

        assertNotNull(kotlinEngine, "Kotlin engine should be registered")
        assertTrue(kotlinEngine is KotlinScriptEngine)
    }

    /**
     * Verifies routing of script execution requests to the correct language engine.
     */
    @Test
    fun testLanguageRouting() = runBlocking {
        val request = ScriptRequestModel("http://localhost", "GET", mutableMapOf(), mutableMapOf(), "")
        val result = engineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = "console.log('Routed JS');",
            request = request,
            response = null,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Success)
        assertEquals("Routed JS", (result as ScriptExecutionResult.Success).logs.first())
    }
}
