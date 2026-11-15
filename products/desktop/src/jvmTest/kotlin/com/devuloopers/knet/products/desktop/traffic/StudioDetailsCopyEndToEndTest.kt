package com.devuloopers.knet.products.desktop.traffic

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.copy.StudioCopySource
import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.application.contract.apistudio.*
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.domain.clientNetwork.model.*
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.di.DesktopModules
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.core.foundation.clipboard.*
import com.devuloopers.knet.ui.desktop.httppanel.copy.StudioDetailsCopyViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.koin.dsl.*
import java.nio.file.Files
import kotlin.test.*

/** Product wiring, real recorder/files/formatters/interpreters, and Compose ClipEntry without capture or network. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class StudioDetailsCopyEndToEndTest {
    @Test fun executionEvidenceReachesClipboardWithoutReadingTrafficAcrossHttpSseAndNativeProtocols() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val root = Files.createTempDirectory("knet-studio-copy-e2e-")
        val application = koinApplication { allowOverride(true); modules(DesktopModules.all + module {
            single { DesktopConfiguration(appDirectory = root) }; single<TrafficQuery> { NoTraffic() }
        }) }
        val database = application.koin.get<KNetDatabase>(); val scope = application.koin.get<CoroutineScope>()
        val owner = ViewModelStore(); val clipboard = MemoryClipboard()
        val recorder = application.koin.get<StudioExportRecorder>()
        val sessions = mutableListOf<StudioExportSession>()
        try {
            assertSame<Any>(recorder, application.koin.get<StudioCopySource>())
            val viewModel = application.koin.get<StudioDetailsCopyViewModel>(); owner.put("copy", viewModel)
            suspend fun copy(session: StudioExportSession, preset: TrafficCopyPreset): String {
                viewModel.selectOwner(session.id); viewModel.copy(session.id, preset) { clipboard.setPlainText(it) }
                withTimeout(5_000) { viewModel.state.first { !it.copying } }
                assertNull(viewModel.state.value.error, preset.name)
                return clipboard.readPlainText().orEmpty()
            }
            val http = recorder.http(StudioExportHttpRequest("https://original.test/graphql?operationName=Q", HttpMethod.POST,
                listOf("Content-Type" to "application/json"), OutboundRequestBody.Json("{\"query\":\"query Q { id }\",\"variables\":{\"id\":1}}"))).also(sessions::add)
            http.response(HttpExecutionResponseHead(503, "Service Unavailable", mapOf("Content-Type" to "application/json"), emptyMap(), ApplicationProtocol.fromToken("HTTP/2"),
                listOf("Content-Type" to "application/json", "X-Repeated" to "one", "X-Repeated" to "two", "Set-Cookie" to "a=1; Secure")))
            http.append("{\"actual\":true}".encodeToByteArray()); http.complete(ExecutionResult(503, "Service Unavailable"))
            assertEquals("{\n  \"actual\": true\n}", copy(http, TrafficCopyPreset.RESPONSE_BODY))
            assertEquals("{\"actual\":true}", copy(http, TrafficCopyPreset.RESPONSE_ORIGINAL_TEXT))
            assertContains(copy(http, TrafficCopyPreset.RESPONSE_HEADERS), "X-Repeated: one\nX-Repeated: two")
            assertEquals("a=1; Secure", copy(http, TrafficCopyPreset.RESPONSE_COOKIES))
            assertContains(copy(http, TrafficCopyPreset.GRAPHQL_QUERY), "query Q")
            assertContains(copy(http, TrafficCopyPreset.FULL), "503 Service Unavailable")
            val sse = recorder.http(StudioExportHttpRequest("https://original.test/events", HttpMethod.GET, emptyList(), OutboundRequestBody.None)).also(sessions::add)
            sse.response(HttpExecutionResponseHead(200, "OK", mapOf("Content-Type" to "text/event-stream"), emptyMap(), ApplicationProtocol.fromToken("HTTP/2")))
            sse.append("event: value\ndata: {\"n\":1}\n\ndata: incomplete".encodeToByteArray())
            val data = copy(sse, TrafficCopyPreset.SSE_DATA)
            assertContains(data, "\"n\": 1"); assertContains(data, "still active"); assertFalse(data.contains("incomplete\n"))
            for (kind in listOf(RequestKindId.WEBSOCKET, RequestKindId.GRAPHQL_WEBSOCKET, RequestKindId.GRPC)) {
                val native = recorder.protocol(ApiStudioProtocolExecutionCommand(ApiStudioProtocolDocument("doc", "Native", kind, 1, byteArrayOf())),
                    ApiStudioProtocolDraft("doc", "Native", "original.test", 443, true, "service/Call", 1000, emptyList(), emptyList(), null, "wss://original.test/socket")).also(sessions::add)
                native.event(ApiStudioProtocolExecutionEvent.Started("connected", serverResponseReceived = true))
                native.event(ApiStudioProtocolExecutionEvent.Message(ApiStudioProtocolMessage(1, ApiStudioProtocolMessageDirection.INBOUND, "application/json", "{\"native\":true}", if (kind == RequestKindId.GRPC) byteArrayOf(8, 1) else "{\"native\":true}".encodeToByteArray())))
                assertContains(copy(native, if (kind == RequestKindId.GRPC) TrafficCopyPreset.GRPC_MESSAGES else TrafficCopyPreset.WEBSOCKET_MESSAGES), "\"native\": true")
            }
            // A retired execution must never be replaced by another session or a current editor draft.
            viewModel.selectOwner(http.id); http.release()
            val previous = clipboard.readPlainText()
            viewModel.copy(http.id, TrafficCopyPreset.FULL) { clipboard.setPlainText(it) }
            withTimeout(5_000) { viewModel.state.first { !it.copying } }
            assertNotNull(viewModel.state.value.error); assertEquals(previous, clipboard.readPlainText())
        } finally {
            sessions.forEach { it.release() }; owner.clear();
            (recorder as com.devuloopers.knet.data.desktop.export.DesktopStudioExportRecorder).awaitCleanup(); application.close(); scope.coroutineContext.job.join(); database.close()
            root.toFile().deleteRecursively(); Dispatchers.resetMain()
        }
    }
    private class MemoryClipboard : Clipboard {
        private var entry: ClipEntry? = null
        override suspend fun getClipEntry() = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
    }
    private class NoTraffic : TrafficQuery {
        override val generations = emptyFlow<TrafficGeneration>()
        override suspend fun query(query: TrafficPageQuery): TrafficPage = error("Studio copy must not query Traffic")
        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot = error("Studio copy must not resolve Traffic")
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = error("Studio copy must not read Traffic bodies")
    }
}
