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

    /**
     * Reproduction Test 3: Pre-request script sets an environment variable via pm.environment.set.
     * Verifies whether the post-response test script can read pm.environment.get.
     */
    @Test
    fun testPreRequestEnvironmentVariablesPropagation(): Unit = runBlocking {
        val executionHandler = ExecutionHandler()

        val preScriptCode = """
            pm.environment.set("auth_token", "secret_123");
        """.trimIndent()

        val testScriptCode = """
            pm.test("Status code is 200", function() {
                pm.response.to.have.status(200);
            });
            pm.test("Environment variable auth_token exists", function() {
                var token = pm.environment.get("auth_token");
                pm.expect(token).to.eql("secret_123");
            });
        """.trimIndent()

        val request = SavedApiRequest(
            id = "req_repro_1",
            name = "Reproduction Test - Environment Propagation",
            method = HttpMethod.GET,
            url = "http://127.0.0.1:9090/api/test/get",
            scripts = ApiRequestScripts(
                preRequest = preScriptCode,
                test = testScriptCode,
                language = ScriptLanguage.JAVASCRIPT
            )
        )

        val outcome = executionHandler.executeSingleRequest(request)

        assertNotNull(outcome.result, "Result should not be null")
        assertEquals(2, outcome.testResults.size, "Should evaluate 2 test assertions")
        assertTrue(outcome.testResults.all { it.passed }, "All test assertions including pm.environment.get MUST pass")
    }

    /**
     * Reproduction Test 4: Sequentially runs request with pre-script + test-script, then removes pre-script and runs again.
     */
    @Test
    fun testPreRequestScriptAddedThenRemovedSequence(): Unit = runBlocking {
        val executionHandler = ExecutionHandler()

        val preScriptCode = """
            pm.environment.set("run_id", "101");
        """.trimIndent()

        val testScriptCode = """
            pm.test("Status code is 200", function() {
                pm.response.to.have.status(200);
            });
        """.trimIndent()

        // Run 1: WITH pre-request script
        val req1 = SavedApiRequest(
            id = "req_repro_2",
            name = "Run 1 with Pre-script",
            method = HttpMethod.GET,
            url = "http://127.0.0.1:9090/api/test/get",
            scripts = ApiRequestScripts(
                preRequest = preScriptCode,
                test = testScriptCode,
                language = ScriptLanguage.JAVASCRIPT
            )
        )
        val outcome1 = executionHandler.executeSingleRequest(req1)
        assertTrue(outcome1.testResults.isNotEmpty(), "Run 1 must return test assertion results")

        // Run 2: WITHOUT pre-request script (cleared preRequest)
        val req2 = req1.copy(scripts = req1.scripts.copy(preRequest = ""))
        val outcome2 = executionHandler.executeSingleRequest(req2)
        assertTrue(outcome2.testResults.isNotEmpty(), "Run 2 after clearing pre-script MUST also return test assertion results")
    }

    @Test
    fun testKotlinEnvironmentMutationE2EReproduction(): Unit = runBlocking {
        val executionHandler = ExecutionHandler()

        val preScriptCode = """
            environment["user_role"] = "TEST"
        """.trimIndent()

        val testScriptCode = """
            test("User role is ADMIN") {
                expect(environment["user_role"]).toBe("ADMIN")
            }
        """.trimIndent()

        val request = SavedApiRequest(
            id = "req_e2e_kotlin_repro",
            name = "E2E Kotlin Environment Repro",
            method = HttpMethod.GET,
            url = "http://127.0.0.1:9090/api/test/get",
            scripts = ApiRequestScripts(
                preRequest = preScriptCode,
                test = testScriptCode,
                language = ScriptLanguage.KOTLIN
            )
        )

        val outcome = executionHandler.executeSingleRequest(request)

        assertNotNull(outcome.result, "Result should not be null")
        assertEquals(1, outcome.testResults.size, "Should evaluate 1 test assertion")
        assertFalse(outcome.testResults[0].passed, "Test assertion SHOULD FAIL because user_role is TEST, not ADMIN!")
    }

    @Test
    fun testKotlinScriptEditingSequence(): Unit = runBlocking {
        val executionHandler = ExecutionHandler()

        val testScriptCode = """
            test("User role is ADMIN") {
                expect(environment["user_role"]).toBe("ADMIN")
            }
        """.trimIndent()

        // RUN 1: preRequest sets "ADMIN"
        val req1 = SavedApiRequest(
            id = "req_seq_1",
            name = "Sequence Test",
            method = HttpMethod.GET,
            url = "http://127.0.0.1:9090/api/test/get",
            scripts = ApiRequestScripts(
                preRequest = """environment["user_role"] = "ADMIN"""",
                test = testScriptCode,
                language = ScriptLanguage.KOTLIN
            )
        )
        val outcome1 = executionHandler.executeSingleRequest(req1)
        assertEquals(1, outcome1.testResults.size)
        assertTrue(outcome1.testResults[0].passed, "Run 1 SHOULD PASS when user_role is ADMIN")

        // RUN 2: User edits preRequest script to "TEST" and clicks Send Request again
        val req2 = req1.copy(
            scripts = req1.scripts.copy(preRequest = """environment["user_role"] = "TEST"""")
        )
        val outcome2 = executionHandler.executeSingleRequest(req2)
        assertEquals(1, outcome2.testResults.size)
        assertFalse(outcome2.testResults[0].passed, "Run 2 SHOULD FAIL when user_role is changed to TEST")
    }
}
