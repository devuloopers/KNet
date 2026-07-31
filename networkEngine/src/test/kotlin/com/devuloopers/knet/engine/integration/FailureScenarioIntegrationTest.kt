package com.devuloopers.knet.engine.integration

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Integration test suite for failure scenarios (syntax errors, compilation errors, runtime exceptions).
 */
class FailureScenarioIntegrationTest {

    private val scriptEngineManager = ScriptEngineManager()
    private val mockRequest = ScriptRequestModel("http://127.0.0.1:9090/api/test/get", "GET", mutableMapOf(), mutableMapOf(), "")
    private val mockResponse = ScriptResponseModel(200, "OK", 50L, 100L, emptyMap(), "{}")

    /**
     * Verifies that script compilation errors return [ScriptExecutionResult.Error] rather than swallowing exceptions.
     */
    @Test
    fun testScriptCompilationErrorHandling(): Unit = runBlocking {
        val invalidScript = """
            val unclosedString = "syntax error here...
        """.trimIndent()

        val result = scriptEngineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = invalidScript,
            request = mockRequest,
            response = mockResponse,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Error, "Compilation error must return Error result state")
    }

    /**
     * Verifies that security sandbox violations (e.g. System.exit) return an Error state.
     */
    @Test
    fun testSecuritySandboxViolationHandling(): Unit = runBlocking {
        val forbiddenScript = """
            System.exit(0)
        """.trimIndent()

        val result = scriptEngineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = forbiddenScript,
            request = mockRequest,
            response = mockResponse,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Error, "Forbidden sandbox keywords must be blocked")
    }
}
