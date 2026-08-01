package com.devuloopers.knet.engine.script.kotlin

import com.devuloopers.knet.engine.script.TestFixtures
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.kotlin.runtime.ExpressionRuntime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpressionRuntimeTest {

    private val runtime = ExpressionRuntime()

    @Test
    fun testExpressionEvaluation() = runBlocking {
        val req = TestFixtures.createSampleRequest()
        val resp = TestFixtures.createSampleResponse()
        val env = EnvironmentStore()

        val exprCode = """
            env["auth"] = "bearer"
            test("Status is 200") {
                response.statusCode == 200
            }
        """.trimIndent()

        val result = runtime.execute(exprCode, req, resp, env)
        assertTrue(result is ScriptExecutionResult.Success)
        assertEquals("bearer", env["auth"])
        assertEquals(1, result.testResults.size)
    }
}
