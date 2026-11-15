package com.devuloopers.knet.products.desktop.traffic

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.export.CaptureExportSource
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.di.DesktopModules
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.message.*
import com.devuloopers.knet.ui.core.foundation.clipboard.readPlainText
import com.devuloopers.knet.ui.core.foundation.clipboard.setPlainText
import com.devuloopers.knet.ui.desktop.traffic.copy.TrafficDetailsCopyViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.koin.dsl.*
import java.nio.file.Files
import kotlin.test.*

/** Real product DI, device Room names, body files, bounded formatters and Compose ClipEntry conversion. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class TrafficDetailsCopyEndToEndTest {
    @Test fun selectedCanonicalDetailsReachComposeClipboardWithPrettyBodiesGraphQLAndDeviceIsolation() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val root = Files.createTempDirectory("knet-copy-details-")
        val query = Query()
        val frames = Frames()
        val application = koinApplication { allowOverride(true); modules(DesktopModules.all + module {
            single { DesktopConfiguration(appDirectory = root) }; single<TrafficQuery> { query }; single<ProtocolMessageQuery> { frames }
        }) }
        val database = application.koin.get<KNetDatabase>(); val scope = application.koin.get<CoroutineScope>(); val owner = ViewModelStore()
        val clipboard = MemoryClipboard()
        try {
            database.registeredDeviceDao().upsertRegisteredDevice(RegisteredDeviceEntity("a", "Pixel 8", "PAIRED_COMPANION", 1, 1, null))
            val bodies = application.koin.get<BodyStore>()
            val bytes = "{\"ok\":true}".encodeToByteArray()
            val writer = bodies.openWrite(BodyId("copy-response"), BodyWritePolicy(bytes.size.toLong()))
            writer.append(bytes); val ref = writer.complete().body
            query.body = MessageBodyRef.Available(ref)
            val viewModel = application.koin.get<TrafficDetailsCopyViewModel>(); owner.put("copy", viewModel)
            val source = TrafficSource.Device(RegisteredDeviceId("a")); viewModel.selectSource(source)
            val ids = listOf("older", "graphql").map { TrafficRecordId.Exchange(ExchangeId(it)) }
            viewModel.copy(CaptureExportSource.Selection(ids, source)) { clipboard.setPlainText(it) }
            withTimeout(5_000) { viewModel.state.first { !it.copying } }
            val text = assertNotNull(clipboard.readPlainText())
            assertNull(viewModel.state.value.error); assertContains(text, "Device: Pixel 8")
            assertContains(text, "TRAFFIC DETAILS - 2 ITEMS"); assertContains(text, "1. GET /graphql")
            assertContains(text, "Operation name:\n    Q"); assertContains(text, "Query:\n    query Q")
            assertContains(text, "Variables:\n    {\n      \"id\": 1")
            assertContains(text, "Body:\n    {\n      \"ok\": true\n    }")
            assertEquals(1, Regex("----------------------------------------").findAll(text).count())
            for (preset in TrafficCopyPreset.entries) {
                viewModel.copy(CaptureExportSource.Selection(ids, source), preset) { clipboard.setPlainText(it) }
                withTimeout(5_000) { viewModel.state.first { !it.copying } }
                assertNull(viewModel.state.value.error, preset.name)
                val pasted = assertNotNull(clipboard.readPlainText(), preset.name)
                assertTrue(pasted.isNotBlank(), preset.name)
                if (preset == TrafficCopyPreset.URL) assertEquals("https://copy.test:443/graphql?query=query+Q%28%24id%3AInt%29%7Buser%28id%3A%24id%29%7Bid%7D%7D&operationName=Q&variables=%7B%22id%22%3A1%7D\nhttps://copy.test:443/users?page=1", pasted)
                if (preset == TrafficCopyPreset.RESPONSE_BODY) assertContains(pasted, "\"ok\": true")
                if (preset == TrafficCopyPreset.STRUCTURED_JSON) assertContains(pasted, "\"version\": 1")
            }
            val eventBytes = "event: update\ndata: {\"ok\":true}\n\n".encodeToByteArray()
            val eventWriter = bodies.openWrite(BodyId("copy-event"), BodyWritePolicy(eventBytes.size.toLong()))
            eventWriter.append(eventBytes)
            frames.page = ProtocolMessagePage(listOf(ProtocolMessageSnapshot(ProtocolMessageId("sse"), ConnectionId("a"), ExchangeId("sse"), null,
                MessageProtocolId.SSE, ProtocolMessageKind.RECORD, TrafficDirection.SERVER_TO_CLIENT, 1, 1, null, eventBytes.size.toLong(), false, null,
                MessageBodyRef.Available(eventWriter.complete().body), ProtocolMessageState.COMPLETE)), null, 1)
            viewModel.copy(CaptureExportSource.Selection(listOf(TrafficRecordId.Exchange(ExchangeId("sse"))), source), TrafficCopyPreset.SSE_DATA) { clipboard.setPlainText(it) }
            withTimeout(5_000) { viewModel.state.first { !it.copying } }
            assertNull(viewModel.state.value.error)
            assertContains(clipboard.readPlainText().orEmpty(), "\"ok\": true")
            assertFalse(clipboard.readPlainText().orEmpty().contains("data:"))
            val previous = clipboard.readPlainText()
            viewModel.copy(CaptureExportSource.Selection(listOf(TrafficRecordId.Exchange(ExchangeId("foreign"))), source)) { clipboard.setPlainText(it) }
            withTimeout(5_000) { viewModel.state.first { !it.copying } }
            assertContains(viewModel.state.value.error.orEmpty(), "another device"); assertEquals(previous, clipboard.readPlainText())
        } finally {
            owner.clear(); application.close(); scope.coroutineContext.job.join(); database.close()
            root.toFile().deleteRecursively(); Dispatchers.resetMain()
        }
    }
    private class MemoryClipboard : Clipboard {
        private var entry: ClipEntry? = null
        override suspend fun getClipEntry() = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
    }
    private class Frames : ProtocolMessageQuery {
        var page = ProtocolMessagePage(emptyList(), null, 0)
        override fun observeChanges(exchangeId: ExchangeId) = emptyFlow<Long>()
        override suspend fun queryMessages(query: ProtocolMessagePageQuery) = page
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = error("Pinned body store only")
    }
    private class Query : TrafficQuery {
        var body: MessageBodyRef = MessageBodyRef.Empty
        override val generations = emptyFlow<TrafficGeneration>()
        override suspend fun query(query: TrafficPageQuery): TrafficPage = error("Exact selection only")
        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot {
            val graphql = exchangeId.value == "graphql"
            return HttpExchangeSnapshot(exchangeId, ConnectionId(if (exchangeId.value == "foreign") "b" else "a"),
                request = HttpRequestSnapshot(RequestHead(HttpMethod.GET, RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("copy.test", 443),
                    if (graphql) "/graphql?query=query+Q%28%24id%3AInt%29%7Buser%28id%3A%24id%29%7Bid%7D%7D&operationName=Q&variables=%7B%22id%22%3A1%7D" else "/users?page=1"),
                    ApplicationProtocol.fromToken("HTTP/1.1"), listOf(HeaderField(HeaderName("Accept"), "application/json")))),
                response = HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(200), "OK", listOf(HeaderField(HeaderName("Content-Type"), "application/json"))), body),
                state = ExchangeState.COMPLETED, startedAtEpochMillis = if (graphql) 2 else 1)
        }
        override suspend fun getConnectionIngress(connectionId: ConnectionId) = TrafficConnectionIngress(IngressContext(IngressKind.LanPairedDevice, ClientIdentity(connectionId.value)), "192.0.2.1")
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = error("Pinned body store only")
    }
}
