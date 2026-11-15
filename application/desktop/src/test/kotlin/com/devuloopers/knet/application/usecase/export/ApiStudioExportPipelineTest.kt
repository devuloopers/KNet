package com.devuloopers.knet.application.usecase.export

import com.devuloopers.knet.application.contract.apistudio.*
import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.application.contract.script.*
import com.devuloopers.knet.application.usecase.apistudio.*
import com.devuloopers.knet.domain.clientNetwork.executor.*
import com.devuloopers.knet.domain.clientNetwork.model.*
import com.devuloopers.knet.domain.clientNetwork.usecase.*
import com.devuloopers.knet.domain.collection.model.*
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import java.io.IOException
import kotlin.test.*

/** Application execution tests ensure ancillary export retention cannot change transport/script behavior. */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiStudioExportPipelineTest {
    private class Session : StudioExportSession {
        override val id = "execution"
        override val eligible = MutableStateFlow(false)
        var released = false
        var failWrites = false
        var interrupted = false
        val bytes = mutableListOf<Byte>()
        override suspend fun response(head: HttpExecutionResponseHead) { eligible.value = head.headers["Content-Type"] == "text/event-stream" }
        override suspend fun append(bytes: ByteArray) { if (failWrites) throw IOException("No space left"); this.bytes += bytes.toList() }
        override suspend fun complete(result: ExecutionResult) { if (!released) eligible.value = result.statusCode > 0 && result.failureReason == null }
        override suspend fun event(event: ApiStudioProtocolExecutionEvent) = Unit
        override fun interrupted() { interrupted = true }
        override fun release() { released = true; eligible.value = false }
    }
    private class Recorder : StudioExportRecorder {
        val session = Session()
        var request: StudioExportHttpRequest? = null
        var failStart = false
        override suspend fun http(request: StudioExportHttpRequest): StudioExportSession {
            if (failStart) throw IOException("No export storage")
            this.request = request; return session
        }
        override fun protocol(command: ApiStudioProtocolExecutionCommand, draft: ApiStudioProtocolDraft): StudioExportSession = session
    }
    private class Transport : HttpExecutor, HttpStreamingExecutor {
        var wait = false
        var emitHead = true
        var status = 503
        var sse = false
        override suspend fun execute(url: String, method: HttpMethod, headers: Map<String, String>, body: OutboundRequestBody,
            auth: ApiRequestAuth, proxyPort: Int?, httpVersionPreference: HttpVersionPreference) = ExecutionResult(status, "Response")
        override fun executeStreaming(url: String, method: HttpMethod, headers: Map<String, String>, body: OutboundRequestBody,
            auth: ApiRequestAuth, proxyPort: Int?, httpVersionPreference: HttpVersionPreference): Flow<HttpExecutionEvent> = flow {
            if (emitHead) emit(HttpExecutionEvent.ResponseHead(HttpExecutionResponseHead(status, "Response", if (sse) mapOf("Content-Type" to "text/event-stream") else emptyMap(), emptyMap(), null)))
            if (wait) awaitCancellation()
            emit(HttpExecutionEvent.BodyChunk(HttpExecutionBodyChunk(byteArrayOf(0, -1, 2))))
            emit(HttpExecutionEvent.Completed(ExecutionResult(status, "Response", responseBody = "decoded preview", responseSizeBytes = 3)))
        }
        override fun close() = Unit
    }
    private fun request() = SavedApiRequest("request", "Request", method = HttpMethod.POST, url = "https://before.test",
        body = ApiRequestBody(content = "original", type = RequestBodyType.JSON), scripts = ApiRequestScripts(preRequest = "script"))

    @Test fun `archive receives post script request and original bytes while HTTP error remains exportable`() = runTest {
        val recorder = Recorder(); val transport = Transport()
        val script = ScriptExecutor { command -> ScriptExecutionOutcome.Success(command.request.copy(url = "https://after.test", body = "scripted"), emptyList(), emptyMap(), emptyList()) }
        val workflow = ExecuteApiStudioRequestUseCase(ExecuteClientApiRequestUseCase(transport), FormatResponseBodyUseCase(), script, StandardTestDispatcher(testScheduler), recorder)
        val completed = workflow.executeStreaming(request(), null).filterIsInstance<ApiStudioHttpExecutionEvent.Completed>().single()
        assertEquals("https://after.test", recorder.request?.url)
        assertEquals(OutboundRequestBody.Json("scripted"), recorder.request?.body)
        assertContentEquals(byteArrayOf(0, -1, 2), recorder.session.bytes.toByteArray())
        assertTrue(completed.value.exportSession!!.eligible.value)
        assertFalse(recorder.session.released)
        completed.value.exportSession!!.release()
    }

    @Test fun `full disk while opening export store does not prevent sending or receiving`() = runTest {
        val recorder = Recorder().apply { failStart = true }
        val workflow = ExecuteApiStudioRequestUseCase(ExecuteClientApiRequestUseCase(Transport()), FormatResponseBodyUseCase(), UnavailableScriptExecutor, StandardTestDispatcher(testScheduler), recorder)
        val result = workflow.executeStreaming(request(), null).filterIsInstance<ApiStudioHttpExecutionEvent.Completed>().single().value
        assertEquals(503, result.result.statusCode); assertNull(result.exportSession)
    }

    @Test fun `payload spool failure retires export but request still completes with received response`() = runTest {
        val recorder = Recorder().apply { session.failWrites = true }
        val workflow = ExecuteApiStudioRequestUseCase(ExecuteClientApiRequestUseCase(Transport()), FormatResponseBodyUseCase(), UnavailableScriptExecutor, StandardTestDispatcher(testScheduler), recorder)
        val result = workflow.executeStreaming(request(), null).filterIsInstance<ApiStudioHttpExecutionEvent.Completed>().single().value
        assertEquals(503, result.result.statusCode); assertTrue(recorder.session.released); assertFalse(result.exportSession!!.eligible.value)
    }

    @Test fun `cancellation before response releases execution that presentation never received`() = runTest {
        val recorder = Recorder(); val transport = Transport().apply { emitHead = false; wait = true }
        val workflow = ExecuteApiStudioRequestUseCase(ExecuteClientApiRequestUseCase(transport), FormatResponseBodyUseCase(), UnavailableScriptExecutor, StandardTestDispatcher(testScheduler), recorder)
        val job = launch { workflow.executeStreaming(request(), null).collect() }
        runCurrent(); job.cancelAndJoin()
        assertTrue(recorder.session.interrupted); assertTrue(recorder.session.released)
    }

    @Test fun `cancellation after live response keeps partial export owned by received result until clear`() = runTest {
        val recorder = Recorder(); val transport = Transport().apply { sse = true; wait = true }
        val workflow = ExecuteApiStudioRequestUseCase(ExecuteClientApiRequestUseCase(transport), FormatResponseBodyUseCase(), UnavailableScriptExecutor, StandardTestDispatcher(testScheduler), recorder)
        var received: StudioExportSession? = null
        val job = launch { workflow.executeStreaming(request(), null).collect { if (it is ApiStudioHttpExecutionEvent.ResponseHead) received = it.exportSession } }
        runCurrent(); job.cancelAndJoin()
        assertNotNull(received); assertTrue(recorder.session.interrupted); assertFalse(recorder.session.released)
        received!!.release(); assertTrue(recorder.session.released)
    }
}
