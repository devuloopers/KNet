package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.port.script.ScriptExecutionOutcome
import com.devuloopers.knet.application.port.script.ScriptExecutionPort
import com.devuloopers.knet.application.port.script.UnavailableScriptExecutionPort
import com.devuloopers.knet.application.port.traffic.RecordHttpExchangeCommand
import com.devuloopers.knet.application.port.traffic.TrafficRecordPort
import com.devuloopers.knet.application.port.traffic.TrafficRecordReceipt
import com.devuloopers.knet.application.usecase.traffic.RecordHttpExchangeUseCase
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
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
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Verifies UI-neutral API Studio execution, filtering, and canonical direct recording. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExecuteApiStudioRequestUseCaseTest {

    @Test
    fun `execution sends enabled authored rows and records direct traffic once`() = runTest {
        val executor = CapturingExecutor()
        val recordPort = RecordingPort()
        val useCase = ExecuteApiStudioRequestUseCase(
            executeRequest = ExecuteClientApiRequestUseCase(executor),
            formatResponseBody = FormatResponseBodyUseCase(),
            recordHttpExchange = RecordHttpExchangeUseCase(recordPort),
            scriptExecution = UnavailableScriptExecutionPort,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        val request = SavedApiRequest(
            id = "request-1",
            name = "Create item",
            method = HttpMethod.POST,
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
        val body = assertIs<OutboundRequestBody.Multipart>(executor.body)
        assertEquals(listOf("name"), body.fields.map { it.name })

        val command = recordPort.commands.single()
        val target = assertIs<RequestTarget.Absolute>(command.request.target)
        assertEquals("/items?limit=2", target.pathAndQuery)
        assertEquals(ExchangeState.COMPLETED, command.state)

        useCase.execute(request, proxyPort = 8080)
        assertEquals(1, recordPort.commands.size)
    }

    @Test
    fun `scripts mutate the outbound request and publish assertions and ordered logs`() = runTest {
        val executor = CapturingExecutor()
        val recordPort = RecordingPort()
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
            recordHttpExchange = RecordHttpExchangeUseCase(recordPort),
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
    fun `direct recording failure remains non fatal and is visible in execution logs`() = runTest {
        val executor = CapturingExecutor()
        val recordPort = RecordingPort().apply {
            failure = IllegalStateException("Traffic storage is unavailable")
        }
        val useCase = ExecuteApiStudioRequestUseCase(
            executeRequest = ExecuteClientApiRequestUseCase(executor),
            formatResponseBody = FormatResponseBodyUseCase(),
            recordHttpExchange = RecordHttpExchangeUseCase(recordPort),
            scriptExecution = UnavailableScriptExecutionPort,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = useCase.execute(
            SavedApiRequest(
                id = "request-1",
                name = "Get item",
                method = HttpMethod.GET,
                url = "https://api.example.test/items"
            ),
            proxyPort = null
        )

        assertTrue(result.result.isSuccess)
        assertEquals(
            listOf("[Traffic Recording Error] Traffic storage is unavailable"),
            result.consoleLogs
        )
    }

    private class RecordingPort : TrafficRecordPort {
        val commands = mutableListOf<RecordHttpExchangeCommand>()
        var failure: Exception? = null

        override suspend fun record(command: RecordHttpExchangeCommand): TrafficRecordReceipt {
            failure?.let { throw it }
            commands += command
            return TrafficRecordReceipt(CaptureSessionId("direct-session"), command.exchangeId)
        }
    }

    private class CapturingExecutor : HttpExecutor {
        var url: String = ""
        var headers: Map<String, String> = emptyMap()
        var body: OutboundRequestBody = OutboundRequestBody.None

        override suspend fun execute(
            url: String,
            method: HttpMethod,
            headers: Map<String, String>,
            body: OutboundRequestBody,
            auth: ApiRequestAuth,
            proxyPort: Int?
        ): ExecutionResult {
            this.url = url
            this.headers = headers
            this.body = body
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
}
