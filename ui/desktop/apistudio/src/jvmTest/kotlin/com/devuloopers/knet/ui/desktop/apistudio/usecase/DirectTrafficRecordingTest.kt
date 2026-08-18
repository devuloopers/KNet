package com.devuloopers.knet.ui.desktop.apistudio.usecase

import com.devuloopers.knet.application.port.traffic.RecordHttpExchangeCommand
import com.devuloopers.knet.application.port.traffic.TrafficRecordPort
import com.devuloopers.knet.application.port.traffic.TrafficRecordReceipt
import com.devuloopers.knet.application.usecase.traffic.RecordHttpExchangeUseCase
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Verifies API Studio records direct results through the canonical application boundary only. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DirectTrafficRecordingTest {
    @Test
    fun `direct execution records shared request and response heads while proxied execution does not duplicate`() = runTest {
        val recordPort = RecordingPort()
        val useCase = ExecuteScriptedApiRequestUseCase(
            executeUseCase = ExecuteClientApiRequestUseCase(SuccessExecutor()),
            formatResponseBodyUseCase = FormatResponseBodyUseCase(),
            recordHttpExchangeUseCase = RecordHttpExchangeUseCase(recordPort),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val editor = RequestEditorState(
            url = "https://api.example.test/items",
            method = "GET",
            queryParams = listOf("limit" to "2"),
            headers = listOf("Accept" to "application/json"),
            cookies = listOf("session" to "abc"),
        )

        useCase.execute(editorState = editor, proxyPort = null)

        val command = recordPort.commands.single()
        val target = assertIs<RequestTarget.Absolute>(command.request.target)
        assertEquals("api.example.test", target.authority.host)
        assertEquals("/items?limit=2", target.pathAndQuery)
        assertEquals("GET", command.request.method.token)
        assertEquals("200", command.response?.status?.code.toString())
        assertEquals(ExchangeState.COMPLETED, command.state)
        assertEquals(
            "session=abc",
            command.request.headers.single { header -> header.name.value == "Cookie" }.value,
        )

        useCase.execute(editorState = editor, proxyPort = 8080)

        assertEquals(1, recordPort.commands.size)
    }

    private class RecordingPort : TrafficRecordPort {
        val commands = mutableListOf<RecordHttpExchangeCommand>()

        override suspend fun record(command: RecordHttpExchangeCommand): TrafficRecordReceipt {
            commands += command
            return TrafficRecordReceipt(CaptureSessionId("direct-session"), command.exchangeId)
        }
    }

    private class SuccessExecutor : HttpExecutor {
        override suspend fun execute(
            url: String,
            method: HttpMethod,
            headers: Map<String, String>,
            body: OutboundRequestBody,
            auth: ApiRequestAuth,
            proxyPort: Int?,
        ): ExecutionResult = ExecutionResult(
            statusCode = 200,
            statusText = "OK",
            headers = mapOf("Content-Type" to "application/json"),
            responseBody = "{\"ok\":true}",
            timings = ExchangeTimings(totalMillis = 4L),
            responseSizeBytes = 11L,
        )

        override fun close() = Unit
    }
}
