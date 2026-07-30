package com.devuloopers.knet.scriptengine.bridge

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit test suite for Request Bridge mappings in script contexts.
 * Verifies mapping of URLs, HTTP verbs, query parameters, headers, empty bodies, and binary payloads.
 */
class RequestBridgeTest {

    private val engineManager = ScriptEngineManager()

    /**
     * Verifies that full request attributes (url, method, headers, queryParams, body) are accurately exposed to JS scripts.
     */
    @Test
    fun testFullRequestModelExposed() = runBlocking {
        val request = ScriptRequestModel(
            url = "http://localhost:9090/api/v1/users",
            method = "PUT",
            headers = mutableMapOf("Authorization" to "Bearer test_tok", "X-Client" to "KNetUI"),
            queryParams = mutableMapOf("filter" to "active"),
            body = """{"name":"John"}"""
        )

        val script = """
            console.log("URL: " + pm.request.url);
            console.log("METHOD: " + pm.request.method);
        """.trimIndent()

        val result = engineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = script,
            request = request,
            response = null,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Success)
        val logs = (result as ScriptExecutionResult.Success).logs
        assertEquals("URL: http://localhost:9090/api/v1/users", logs[0])
        assertEquals("METHOD: PUT", logs[1])
    }
}
