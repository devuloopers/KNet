package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.message.*
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Live list invalidations must not clear unchanged selected payloads or apply an older selection's result. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolMessageSelectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()
    private val exchange = HttpExchangeSnapshot(ExchangeId("exchange"),
        request = HttpRequestSnapshot(RequestHead(HttpMethod.GET,
            RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("example.test"), "/ws"),
            ApplicationProtocol.fromToken("HTTP/1.1"), emptyList())), state = ExchangeState.COMPLETED, startedAtEpochMillis = 1)
    private val traffic = object : TrafficQuery {
        override val generations = emptyFlow<TrafficGeneration>()
        override suspend fun query(query: TrafficPageQuery) = TrafficPage(
            items = listOf(TrafficPageItem(captureSequence = TrafficCaptureSequence(1),
                historySequence = TrafficHistorySequence(1), exchange = exchange)),
            nextCursor = null, totalCount = 1, generation = 1)
        override suspend fun getExchange(exchangeId: ExchangeId) = exchange
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = error("HTTP bodies are empty")
    }
    private fun message(id: String, sequence: Long) = ProtocolMessageSnapshot(ProtocolMessageId(id), ConnectionId("connection"),
        exchange.id, null, MessageProtocolId.WEBSOCKET, ProtocolMessageKind.TEXT, TrafficDirection.SERVER_TO_CLIENT,
        sequence, sequence, 3, 3, false, null,
        MessageBodyRef.Available(BodyRef(BodyId(id), 3, 3, outcome = BodyCaptureOutcome.Complete)), ProtocolMessageState.COMPLETE)
    private inner class Messages(val slow: Boolean = false) : ProtocolMessageQuery {
        val generation = MutableStateFlow(0L)
        var items = listOf(message("one", 1))
        var reads = 0
        val firstReadStarted = CompletableDeferred<Unit>()
        override fun observeChanges(exchangeId: ExchangeId) = generation
        override suspend fun queryMessages(query: ProtocolMessagePageQuery) = ProtocolMessagePage(items, null, items.size.toLong())
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk {
            reads++
            firstReadStarted.complete(Unit)
            if (slow && bodyId.value == "one") delay(1_000)
            return BodyChunk(bodyId.value.encodeToByteArray(), 0, true)
        }
    }

    @Test fun `live page and repeated selection retain the selected body without another read`() = runTest(dispatcher) {
        val messages = Messages()
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = traffic, customProtocolMessageQuery = messages)
        advanceUntilIdle()
        assertContentEquals("one".encodeToByteArray(), viewModel.uiState.value.protocolMessages.selectedBodyBytes)
        val reads = messages.reads
        viewModel.processIntent(TrafficIntent.SelectProtocolMessage(ProtocolMessageId("one")))
        assertContentEquals("one".encodeToByteArray(), viewModel.uiState.value.protocolMessages.selectedBodyBytes)
        messages.items += message("two", 2); messages.generation.value++
        advanceUntilIdle()
        assertEquals(2L, viewModel.uiState.value.protocolMessages.totalCount)
        assertContentEquals("one".encodeToByteArray(), viewModel.uiState.value.protocolMessages.selectedBodyBytes)
        assertEquals(reads, messages.reads)
        viewModel.processIntent(TrafficIntent.SelectProtocolMessage(ProtocolMessageId("two")))
        advanceUntilIdle()
        assertContentEquals("two".encodeToByteArray(), viewModel.uiState.value.protocolMessages.selectedBodyBytes)
    }

    @Test fun `late payload cannot replace the newly selected message`() = runTest(dispatcher) {
        val messages = Messages(slow = true).apply { items += message("two", 2) }
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = traffic, customProtocolMessageQuery = messages)
        messages.firstReadStarted.await()
        viewModel.processIntent(TrafficIntent.SelectProtocolMessage(ProtocolMessageId("two")))
        advanceUntilIdle()
        assertEquals(ProtocolMessageId("two"), viewModel.uiState.value.protocolMessages.selectedMessageId)
        assertContentEquals("two".encodeToByteArray(), viewModel.uiState.value.protocolMessages.selectedBodyBytes)
    }
}
