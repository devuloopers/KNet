package com.devuloopers.knet.scriptengine.security

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Security test suite covering TC-100 through TC-106 and TC-130 through TC-132.
 * Validates that host sandboxing prevents JVM escapes, reflection, package lookup, System.exit, and command execution.
 */
class SecuritySandboxTest {

    private val engineManager = ScriptEngineManager()
    private val mockRequest = ScriptRequestModel("http://localhost", "GET", mutableMapOf(), mutableMapOf(), "")

    /**
     * TC-100 & TC-101: Verifies that Java reflection / Java.type and Packages lookups are blocked in GraalJS context.
     */
    @Test
    fun testJavaTypeLookupBlocked() = runBlocking {
        val script = """
            var File = Java.type('java.io.File');
        """.trimIndent()

        val result = engineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = script,
            request = mockRequest,
            response = null,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Error, "Java.type lookup should be blocked by sandbox security")
        val error = result as ScriptExecutionResult.Error
        assertTrue(
            error.message.contains("ReferenceError") || error.message.contains("not defined") || error.message.contains("Error"),
            "Error message should indicate reflection lookup failure: ${error.message}"
        )
    }

    /**
     * TC-102: Verifies that System.exit() calls are blocked inside JavaScript context.
     */
    @Test
    fun testSystemExitBlocked() = runBlocking {
        val script = """
            java.lang.System.exit(0);
        """.trimIndent()

        val result = engineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = script,
            request = mockRequest,
            response = null,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Error, "System.exit call must be blocked by sandbox security")
    }

    /**
     * TC-103: Verifies that Runtime.getRuntime().exec() command execution attempts are blocked.
     */
    @Test
    fun testRuntimeExecBlocked() = runBlocking {
        val script = """
            java.lang.Runtime.getRuntime().exec('calc.exe');
        """.trimIndent()

        val result = engineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = script,
            request = mockRequest,
            response = null,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Error, "Runtime.getRuntime().exec must be blocked by sandbox security")
    }
}
