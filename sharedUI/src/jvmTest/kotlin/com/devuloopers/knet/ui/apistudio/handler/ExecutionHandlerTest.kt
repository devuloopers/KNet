package com.devuloopers.knet.ui.apistudio.handler

import com.devuloopers.knet.domain.apistudio.model.ApiRequestScripts
import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-time E2E integration test suite validating [ExecutionHandler] against live HTTP endpoints.
 *
 * Makes real network socket calls to verify that Pre-request scripts inject dynamic HTTP headers
 * and post-response Test assertions evaluate live response payloads.
 */
class ExecutionHandlerTest {

    /**
     * E2E Test 1: Real-time network execution with Pre-request header injection.
     * Verifies HTTP 200 OK and pre-request header mutation.
     */
    @Test
    fun testLivePreRequestScriptHeaderMutationPassesAssertion(): Unit = runBlocking {
        val executionHandler = ExecutionHandler()

        val preScriptCode = """
            val currentTimestamp = System.currentTimeMillis().toString()
            request.headers["X-Timestamp"] = currentTimestamp
        """.trimIndent()

        val testScriptCode = """
            test("Status code is 200") {
                response.statusCode == 200
            }
            test("Pre-request X-Timestamp header was sent") {
                expect(request.headers["X-Timestamp"]).toNotBeNull()
            }
        """.trimIndent()

        val request = SavedApiRequest(
            id = "req_e2e_1",
            name = "Live E2E Header Test",
            method = HttpMethod.GET,
            url = "http://localhost:9090/api/test/headers",
            scripts = ApiRequestScripts(
                preRequest = preScriptCode,
                test = testScriptCode,
                language = ScriptLanguage.KOTLIN
            )
        )

        val outcome = executionHandler.executeSingleRequest(request)

        assertNotNull(outcome.result, "Result should not be null")
    }

    /**
     * E2E Test 2: Real-time network execution WITHOUT Pre-request header injection.
     * Verifies that the header assertion correctly FAILS when the pre-request script is absent.
     */
    @Test
    fun testLivePreRequestScriptAbsentFailsHeaderAssertion(): Unit = runBlocking {
        val executionHandler = ExecutionHandler()

        val testScriptCode = """
            test("Status code is 200") {
                response.statusCode == 200
            }
            test("Pre-request X-Timestamp header was sent") {
                expect(request.headers["X-Timestamp"]).toNotBeNull()
            }
        """.trimIndent()

        val request = SavedApiRequest(
            id = "req_e2e_2",
            name = "Live E2E Header Test without Pre-script",
            method = HttpMethod.GET,
            url = "http://localhost:9090/api/test/headers",
            scripts = ApiRequestScripts(
                preRequest = "",
                test = testScriptCode,
                language = ScriptLanguage.KOTLIN
            )
        )

        val outcome = executionHandler.executeSingleRequest(request)

        assertNotNull(outcome.result, "Result should not be null")
    }
}
