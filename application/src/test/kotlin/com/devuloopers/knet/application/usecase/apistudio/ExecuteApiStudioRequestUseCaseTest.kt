package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.port.script.ScriptExecutionOutcome
import com.devuloopers.knet.application.port.script.ScriptExecutionPort
import com.devuloopers.knet.application.port.script.UnavailableScriptExecutionPort
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionBodyChunk
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionEvent
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.domain.clientNetwork.executor.HttpStreamingExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestBodyField
import com.devuloopers.knet.domain.collection.model.RequestCookie
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.RequestQueryParameter
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.model.ApiRequestScripts
import com.devuloopers.knet.scripting.model.ScriptAssertion
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Verifies UI-neutral API Studio execution and authored-request filtering. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExecuteApiStudioRequestUseCaseTest {

    @Test
    fun `execution sends enabled authored rows for direct and proxied transport`() = runTest {
        val executor = CapturingExecutor()
        val useCase = ExecuteApiStudioRequestUseCase(
            executeRequest = ExecuteClientApiRequestUseCase(executor),
            formatResponseBody = FormatResponseBodyUseCase(),
            scriptExecution = UnavailableScriptExecutionPort,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        val request = SavedApiRequest(
            id = "request-1",
            name = "Create item",
            method = HttpMethod.POST,
            httpVersionPreference = HttpVersionPreference.HTTP_1_0,
            url = "https://api.example.test/items",
            queryParameters = listOf(
                RequestQueryParameter("limit", "2"),
                RequestQueryParameter("draft", "true", isEnabled = false)
            ),
            headers = listOf(
                RequestHeader("Accept", "application/json"),
                RequestHeader("X-Draft", "hidden", isEnabled = false)
            ),
            cookies = listOf(
                RequestCookie("session", "abc"),
                RequestCookie("preview", "true", isEnabled = false)
            ),
            body = ApiRequestBody(
                type = RequestBodyType.FORM_DATA,
                formDataFields = listOf(
                    ApiRequestBodyField("enabled", "name", "KNet"),
                    ApiRequestBodyField("disabled", "secret", "hidden", isEnabled = false)
                )
            )
        )

        useCase.execute(request, proxyPort = null)

        assertEquals("https://api.example.test/items?limit=2", executor.url)
        assertEquals("application/json", executor.headers["Accept"])
        assertFalse("X-Draft" in executor.headers)
        assertEquals("session=abc", executor.headers["Cookie"])
        assertEquals(null, executor.proxyPort)
        assertEquals(HttpVersionPreference.HTTP_1_0, executor.httpVersionPreference)
        val body = assertIs<OutboundRequestBody.Multipart>(executor.body)
        assertEquals(listOf("name"), body.fields.map { it.name })

        useCase.execute(request, proxyPort = 8080)
        assertEquals(8080, executor.proxyPort)
    }

    @Test
    fun `scripts mutate the outbound request and publish assertions and ordered logs`() = runTest {
        val executor = CapturingExecutor()
        var invocation = 0
        val scriptPort = ScriptExecutionPort { command ->
            invocation++
            if (command.response == null) {
                ScriptExecutionOutcome.Success(
                    request = command.request.copy(
                        url = "https://api.example.test/mutated",
                        headers = command.request.headers + ("X-Script" to "true"),
                        queryParameters = mapOf("from" to "script"),
                        body = "{\"mutated\":true}"
                    ),
                    assertions = emptyList(),
                    environment = mapOf("token" to "ready"),
                    logs = listOf("pre")
                )
            } else {
                assertEquals("ready", command.environment["token"])
                assertEquals(200, command.response.statusCode)
                ScriptExecutionOutcome.Success(
                    request = command.request,
                    assertions = listOf(ScriptAssertion("status is 200", passed = true)),
                    environment = command.environment,
                    logs = listOf("post")
                )
            }
        }
        val useCase = ExecuteApiStudioRequestUseCase(
            executeRequest = ExecuteClientApiRequestUseCase(executor),
            formatResponseBody = FormatResponseBodyUseCase(),
            scriptExecution = scriptPort,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        val request = SavedApiRequest(
            id = "request-scripted",
            name = "Scripted request",
            method = HttpMethod.POST,
            url = "https://api.example.test/original",
            body = ApiRequestBody(content = "{}", type = RequestBodyType.JSON),
            scripts = ApiRequestScripts(preRequest = "before()", test = "verify()")
        )

        val result = useCase.execute(request, proxyPort = null)

        assertEquals(2, invocation)
        assertEquals("https://api.example.test/mutated?from=script", executor.url)
        assertEquals("true", executor.headers["X-Script"])
        assertEquals(OutboundRequestBody.Json("{\"mutated\":true}"), executor.body)
        assertEquals(listOf("pre", "post"), result.consoleLogs)
        assertTrue(result.testResults.single().passed)
    }

    @Test
    fun `streaming preserves head chunk and terminal application pipeline order`() = runTest {
        val executor = StreamingExecutor()
        val useCase = ExecuteApiStudioRequestUseCase(
            executeRequest = ExecuteClientApiRequestUseCase(executor),
            formatResponseBody = FormatResponseBodyUseCase(),
            scriptExecution = UnavailableScriptExecutionPort,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val events = useCase.executeStreaming(
            request = SavedApiRequest(
                id = "request-stream",
                name = "Live events",
                method = HttpMethod.GET,
                url = "https://api.example.test/events",
            ),
            proxyPort = null,
        ).toList()

        assertIs<ApiStudioHttpExecutionEvent.ResponseHead>(events[0])
        assertEquals(
            "data: first\n\n",
            assertIs<ApiStudioHttpExecutionEvent.BodyChunk>(events[1]).value.copyBytes().decodeToString(),
        )
        val completed = assertIs<ApiStudioHttpExecutionEvent.Completed>(events[2]).value
        assertEquals(200, completed.result.statusCode)
        assertEquals("", completed.formattedBody)
    }

    private class CapturingExecutor : HttpExecutor {
        var url: String = ""
        var headers: Map<String, String> = emptyMap()
        var body: OutboundRequestBody = OutboundRequestBody.None
        var proxyPort: Int? = null
        var httpVersionPreference: HttpVersionPreference = HttpVersionPreference.AUTO

        override suspend fun execute(
            url: String,
            method: HttpMethod,
            headers: Map<String, String>,
            body: OutboundRequestBody,
            auth: ApiRequestAuth,
            proxyPort: Int?,
            httpVersionPreference: HttpVersionPreference,
        ): ExecutionResult {
            this.url = url
            this.headers = headers
            this.body = body
            this.proxyPort = proxyPort
            this.httpVersionPreference = httpVersionPreference
            return ExecutionResult(
                statusCode = 200,
                statusText = "OK",
                headers = mapOf("Content-Type" to "application/json"),
                responseBody = "{\"ok\":true}",
                timings = ExchangeTimings(totalMillis = 4L),
                responseSizeBytes = 11L
            )
        }

        override fun close(): Unit = Unit
    }

    private class StreamingExecutor : HttpExecutor, HttpStreamingExecutor {
        override suspend fun execute(
            url: String,
            method: HttpMethod,
            headers: Map<String, String>,
            body: OutboundRequestBody,
            auth: ApiRequestAuth,
            proxyPort: Int?,
            httpVersionPreference: HttpVersionPreference,
        ): ExecutionResult = result()

        override fun executeStreaming(
            url: String,
            method: HttpMethod,
            headers: Map<String, String>,
            body: OutboundRequestBody,
            auth: ApiRequestAuth,
            proxyPort: Int?,
            httpVersionPreference: HttpVersionPreference,
        ): Flow<HttpExecutionEvent> = flowOf(
            HttpExecutionEvent.ResponseHead(
                HttpExecutionResponseHead(
                    statusCode = 200,
                    statusText = "OK",
                    headers = mapOf("Content-Type" to "text/event-stream"),
                    cookies = emptyMap(),
                    protocol = null,
                ),
            ),
            HttpExecutionEvent.BodyChunk(HttpExecutionBodyChunk("data: first\n\n".encodeToByteArray())),
            HttpExecutionEvent.Completed(result()),
        )

        override fun close(): Unit = Unit

        private fun result(): ExecutionResult = ExecutionResult(
            statusCode = 200,
            statusText = "OK",
            headers = mapOf("Content-Type" to "text/event-stream"),
        )
    }
}
