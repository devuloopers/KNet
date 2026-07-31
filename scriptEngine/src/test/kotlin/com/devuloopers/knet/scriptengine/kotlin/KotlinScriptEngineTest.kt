package com.devuloopers.knet.scriptengine.kotlin

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit test suite for Kotlin Scripting engine covering TC-003 and TC-020 through TC-022.
 * Verifies Kotlin script execution, variable declarations, test assertions, and fallback parser evaluation.
 */
class KotlinScriptEngineTest {

    private val engineManager = ScriptEngineManager()

    /**
     * TC-020 & TC-021: Tests basic Kotlin script execution and environment variable updates.
     */
    @Test
    fun testKotlinBasicExecution() = runBlocking {
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val script = """
            val x = 10
            env["token"] = "kotlin_token_123"
            test("Kotlin Status Test") {
                response.statusCode == 200
            }
        """.trimIndent()

        val environmentStore = EnvironmentStore()
        val result = engineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = script,
            request = request,
            response = response,
            environment = environmentStore
        )

        if (result is ScriptExecutionResult.Error) {
            fail("Kotlin script execution failed: ${result.message}")
        }

        val success = result as ScriptExecutionResult.Success
        assertEquals(1, success.testResults.size)
        assertTrue(success.testResults[0].passed)
        assertEquals("Kotlin Status Test", success.testResults[0].name)
        assertEquals("kotlin_token_123", success.environmentUpdates["token"])
    }

    @Test
    fun testKotlinRepeatedExecution() = runBlocking {
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val script = """
            test("Kotlin Status Test") {
                response.statusCode == 200
            }
        """.trimIndent()

        val result1 = engineManager.execute(ScriptLanguage.KOTLIN, script, request, response, EnvironmentStore())
        assertTrue(result1 is ScriptExecutionResult.Success)
        assertEquals(1, (result1 as ScriptExecutionResult.Success).testResults.size, "Run 1 should produce 1 test result")

        val result2 = engineManager.execute(ScriptLanguage.KOTLIN, script, request, response, EnvironmentStore())
        assertTrue(result2 is ScriptExecutionResult.Success)
        assertEquals(1, (result2 as ScriptExecutionResult.Success).testResults.size, "Run 2 should produce 1 test result")

        val result3 = engineManager.execute(ScriptLanguage.KOTLIN, script, request, response, EnvironmentStore())
        assertTrue(result3 is ScriptExecutionResult.Success)
        assertEquals(1, (result3 as ScriptExecutionResult.Success).testResults.size, "Run 3 should produce 1 test result")
    }

    @Test
    fun testAlternateScripts() = runBlocking {
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val scriptA = """
            test("Test A") { response.statusCode == 200 }
        """.trimIndent()
        val scriptB = """
            test("Test B1") { response.statusCode == 200 }
            test("Test B2") { response.statusCode == 200 }
        """.trimIndent()

        val resA1 = engineManager.execute(ScriptLanguage.KOTLIN, scriptA, request, response, EnvironmentStore()) as ScriptExecutionResult.Success
        assertEquals(1, resA1.testResults.size)
        assertEquals("Test A", resA1.testResults[0].name)

        val resB1 = engineManager.execute(ScriptLanguage.KOTLIN, scriptB, request, response, EnvironmentStore()) as ScriptExecutionResult.Success
        assertEquals(2, resB1.testResults.size)
        assertEquals("Test B1", resB1.testResults[0].name)
        assertEquals("Test B2", resB1.testResults[1].name)

        val resA2 = engineManager.execute(ScriptLanguage.KOTLIN, scriptA, request, response, EnvironmentStore()) as ScriptExecutionResult.Success
        assertEquals(1, resA2.testResults.size)
        assertEquals("Test A", resA2.testResults[0].name)

        val resB2 = engineManager.execute(ScriptLanguage.KOTLIN, scriptB, request, response, EnvironmentStore()) as ScriptExecutionResult.Success
        assertEquals(2, resB2.testResults.size)
    }

    @Test
    fun testRuntimeExceptionCleanup() = runBlocking {
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val throwingScript = """
            throw RuntimeException("Intentional script failure")
        """.trimIndent()

        val normalScript = """
            test("Normal Test") { response.statusCode == 200 }
        """.trimIndent()

        val errResult = engineManager.execute(ScriptLanguage.KOTLIN, throwingScript, request, response, EnvironmentStore())
        assertTrue(errResult is ScriptExecutionResult.Error)

        // Verify ThreadLocal was cleaned up properly
        kotlin.test.assertNull(com.devuloopers.knet.scriptengine.kotlin.runtime.ResultCollectorHolder.get())

        val okResult = engineManager.execute(ScriptLanguage.KOTLIN, normalScript, request, response, EnvironmentStore())
        assertTrue(okResult is ScriptExecutionResult.Success)
        assertEquals(1, (okResult as ScriptExecutionResult.Success).testResults.size)
        assertEquals("Normal Test", okResult.testResults[0].name)
    }

    @Test
    fun testConcurrentExecution() = runBlocking {
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val deferreds = (1..5).map { index ->
            async(Dispatchers.Default) {
                val script = """
                    test("Concurrent Test $index") { response.statusCode == 200 }
                """.trimIndent()
                val res = engineManager.execute(ScriptLanguage.KOTLIN, script, request, response, EnvironmentStore())
                assertTrue(res is ScriptExecutionResult.Success)
                val testRes = (res as ScriptExecutionResult.Success).testResults
                assertEquals(1, testRes.size)
                assertEquals("Concurrent Test $index", testRes[0].name)
            }
        }
        deferreds.forEach { it.await() }
    }

    @Test
    fun testExpressionRuntimeReproduction() = runBlocking {
        val runtime = com.devuloopers.knet.scriptengine.kotlin.runtime.ExpressionRuntime()

        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val preScript = """
            environment["user_role"] = "TEST"
        """.trimIndent()

        val testScript = """
            test("User role is ADMIN") {
                expect(environment["user_role"]).toBe("ADMIN")
            }
        """.trimIndent()

        val environmentStore = EnvironmentStore()

        // 1. Execute pre-request script
        val preResult = runtime.execute(preScript, request, null, environmentStore)
        assertTrue(preResult is ScriptExecutionResult.Success)
        val envSnapshot = (preResult as ScriptExecutionResult.Success).environmentUpdates
        assertEquals("TEST", envSnapshot["user_role"], "Pre-request script should set user_role to TEST")

        // 2. Execute test script with updated environment
        val testResult = runtime.execute(testScript, request, response, EnvironmentStore(envSnapshot))
        assertTrue(testResult is ScriptExecutionResult.Success)
        val assertions = (testResult as ScriptExecutionResult.Success).testResults
        assertEquals(1, assertions.size, "Should evaluate 1 test assertion")
        kotlin.test.assertFalse(assertions[0].passed, "Test assertion SHOULD FAIL because user_role is TEST, not ADMIN!")
        assertTrue(assertions[0].errorMessage?.contains("Expected 'ADMIN' but got 'TEST'") == true)
    }

    @Test
    fun testExpressionRuntimeAliasAndMutation() = runBlocking {
        val runtime = com.devuloopers.knet.scriptengine.kotlin.runtime.ExpressionRuntime()
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val preScript = """
            environment["role"] = "ADMIN"
        """.trimIndent()

        val testScript = """
            test("Role is ADMIN") {
                expect(env["role"]).toBe("ADMIN")
            }
        """.trimIndent()

        val store = EnvironmentStore()
        val preRes = runtime.execute(preScript, request, null, store) as ScriptExecutionResult.Success
        val testRes = runtime.execute(testScript, request, response, EnvironmentStore(preRes.environmentUpdates)) as ScriptExecutionResult.Success

        assertEquals(1, testRes.testResults.size)
        assertTrue(testRes.testResults[0].passed, "Reverse alias check expect(env['role']).toBe('ADMIN') should pass")
    }

    @Test
    fun testExpressionRuntimeReverseAlias() = runBlocking {
        val runtime = com.devuloopers.knet.scriptengine.kotlin.runtime.ExpressionRuntime()
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val preScript = """
            env["role"] = "ADMIN"
        """.trimIndent()

        val testScript = """
            test("Role is ADMIN") {
                expect(environment["role"]).toBe("ADMIN")
            }
        """.trimIndent()

        val store = EnvironmentStore()
        val preRes = runtime.execute(preScript, request, null, store) as ScriptExecutionResult.Success
        val testRes = runtime.execute(testScript, request, response, EnvironmentStore(preRes.environmentUpdates)) as ScriptExecutionResult.Success

        assertEquals(1, testRes.testResults.size)
        assertTrue(testRes.testResults[0].passed, "Reverse alias check expect(environment['role']).toBe('ADMIN') should pass")
    }

    @Test
    fun testExpressionRuntimeMissingVariable() = runBlocking {
        val runtime = com.devuloopers.knet.scriptengine.kotlin.runtime.ExpressionRuntime()
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val testScript = """
            test("Missing var check") {
                expect(environment["missing"]).toNotBeNull()
            }
        """.trimIndent()

        val testRes = runtime.execute(testScript, request, response, EnvironmentStore()) as ScriptExecutionResult.Success
        assertEquals(1, testRes.testResults.size)
        kotlin.test.assertFalse(testRes.testResults[0].passed, "Missing variable assertion should fail")
    }

    @Test
    fun testNativeKotlinRuntimeBindingsPropagation() = runBlocking {
        val runtime = com.devuloopers.knet.scriptengine.kotlin.runtime.NativeKotlinRuntime()
        if (!runtime.isAvailable()) return@runBlocking

        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val preScript = """environment["user_role"] = "ADMIN""""
        val testScript = """test("User role is ADMIN") { expect(environment["user_role"]).toBe("ADMIN") }"""

        val envStore1 = EnvironmentStore()
        val res1 = runtime.execute(preScript, request, null, envStore1) as ScriptExecutionResult.Success
        assertEquals("ADMIN", res1.environmentUpdates["user_role"])

        val res2 = runtime.execute(testScript, request, response, EnvironmentStore(res1.environmentUpdates)) as ScriptExecutionResult.Success
        assertEquals(1, res2.testResults.size)
        assertTrue(res2.testResults[0].passed, "NativeKotlinRuntime should pass when user_role is ADMIN")
    }
}
