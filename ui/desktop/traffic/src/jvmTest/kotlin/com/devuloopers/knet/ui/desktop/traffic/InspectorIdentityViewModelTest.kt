package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.TrafficGeneration
import com.devuloopers.knet.application.contract.traffic.TrafficCaptureSequence
import com.devuloopers.knet.application.contract.traffic.TrafficHistorySequence
import com.devuloopers.knet.application.contract.traffic.TrafficPage
import com.devuloopers.knet.application.contract.traffic.TrafficPageItem
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.ui.desktop.traffic.model.InspectorLoadState
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class InspectorIdentityViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `late detail result cannot replace the newly selected exchange`() = runTest(dispatcher) {
        val port = DetailPort(
            snapshots = listOf(exchange("fast", 2_000L), exchange("slow", 1_000L)),
            delayedExchangeId = "slow",
        )
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()

        viewModel.processIntent(TrafficIntent.SelectTransaction("slow"))
        runCurrent()
        viewModel.processIntent(TrafficIntent.SelectTransaction("fast"))
        advanceUntilIdle()
        val prepared = viewModel.uiState.value.preparedState

        assertEquals("fast", viewModel.uiState.value.selectedTransactionId)
        assertEquals("fast", prepared.transactionId)
        assertEquals("fast", prepared.exchange?.id?.value)
        assertEquals(InspectorLoadState.READY, prepared.loadState)
    }

    @Test
    fun `completed exchange with empty bodies is reused from the prepared cache`() = runTest(dispatcher) {
        val port = DetailPort(listOf(exchange("newest", 2_000L), exchange("older", 1_000L)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        assertEquals(1, port.detailReads.getValue("newest"))

        viewModel.processIntent(TrafficIntent.SelectTransaction("older"))
        advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.SelectTransaction("newest"))
        assertEquals(InspectorLoadState.READY, viewModel.uiState.value.preparedState.loadState,
            "A cached selection must not publish an intermediate empty loading state.")
        assertEquals("newest", viewModel.uiState.value.preparedState.exchange?.id?.value)
        advanceUntilIdle()

        assertEquals(1, port.detailReads.getValue("newest"))
        assertEquals("", viewModel.uiState.value.preparedState.requestBodyText)
        assertEquals("", viewModel.uiState.value.preparedState.responseBodyText)
    }

    @Test
    fun `cached annotations stay visible while the selected observer reconnects`() = runTest(dispatcher) {
        val owner = ExchangeId("newest")
        val annotation = com.devuloopers.knet.traffic.inspection.InspectionAnnotation(owner,
            com.devuloopers.knet.traffic.inspection.InspectorId("graphql"), 1,
            com.devuloopers.knet.traffic.inspection.InspectionAnnotationState.COMPLETED,
            com.devuloopers.knet.traffic.inspection.InspectionDocument("graphql", "GraphQL query", fields = listOf(
                com.devuloopers.knet.traffic.inspection.InspectionField("Operation", "GetAccount"))), createdAtEpochMillis = 1)
        val annotations = MutableStateFlow(mapOf(owner to listOf(annotation)))
        val store = object : com.devuloopers.knet.application.contract.inspection.InspectionAnnotationStore {
            override suspend fun put(sessionId: com.devuloopers.knet.traffic.id.CaptureSessionId,
                annotation: com.devuloopers.knet.traffic.inspection.InspectionAnnotation) = Unit
            override suspend fun get(exchangeId: ExchangeId) = annotations.value[exchangeId].orEmpty()
            override fun observe(exchangeId: ExchangeId) = flow {
                delay(200); emitAll(annotations.map { it[exchangeId].orEmpty() })
            }
            override fun observe(exchangeIds: Set<ExchangeId>) = annotations.map { it.filterKeys { id -> id in exchangeIds } }
        }
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort =
            DetailPort(listOf(exchange("newest", 2_000), exchange("older", 1_000))), customInspectionAnnotationPort = store)
        advanceUntilIdle()
        assertEquals(listOf(annotation), viewModel.uiState.value.preparedState.annotations)
        viewModel.processIntent(TrafficIntent.SelectTransaction("older")); advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.SelectTransaction("newest"))
        assertEquals(listOf(annotation), viewModel.uiState.value.preparedState.annotations)
        runCurrent()
        assertEquals(listOf(annotation), viewModel.uiState.value.preparedState.annotations,
            "The observer's initial state must not erase cached protocol details.")
        advanceUntilIdle()
        annotations.value = emptyMap(); advanceUntilIdle()
        assertEquals(emptyList(), viewModel.uiState.value.preparedState.annotations,
            "A real annotation removal must still update the selected inspector.")
    }

    @Test
    fun `active exchange is refreshed on revisit instead of caching an unfinished body`() = runTest(dispatcher) {
        val active = exchange("newest", 2_000).copy(state = ExchangeState.RESPONSE_STREAMING, terminalOutcome = null)
        val port = DetailPort(listOf(active, exchange("older", 1_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.SelectTransaction("older")); advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.SelectTransaction("newest"))
        assertEquals(InspectorLoadState.LOADING, viewModel.uiState.value.preparedState.loadState)
        advanceUntilIdle()
        assertEquals(2, port.detailReads.getValue("newest"))
    }

    private class DetailPort(
        private val snapshots: List<HttpExchangeSnapshot>,
        private val delayedExchangeId: String? = null,
    ) : TrafficQuery {
        override val generations: Flow<TrafficGeneration> = emptyFlow()
        val detailReads = mutableMapOf<String, Int>()

        override suspend fun query(query: TrafficPageQuery): TrafficPage = TrafficPage(
            items = snapshots.mapIndexed { index, snapshot ->
                TrafficPageItem(
                    captureSequence = TrafficCaptureSequence((snapshots.size - index).toLong()),
                    historySequence = TrafficHistorySequence((snapshots.size - index).toLong()),
                    exchange = snapshot,
                )
            },
            nextCursor = null,
            totalCount = snapshots.size.toLong(),
            generation = 1L,
        )

        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? {
            detailReads[exchangeId.value] = detailReads.getOrDefault(exchangeId.value, 0) + 1
            if (exchangeId.value == delayedExchangeId) delay(1_000L)
            return snapshots.firstOrNull { snapshot -> snapshot.id == exchangeId }
        }

        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
            error("Empty-body inspector fixtures must not read body storage.")
    }

    private companion object {
        fun exchange(id: String, timestamp: Long): HttpExchangeSnapshot = HttpExchangeSnapshot(
            id = ExchangeId(id),
            request = HttpRequestSnapshot(
                head = RequestHead(
                    method = HttpMethod.fromToken("GET"),
                    target = RequestTarget.Absolute(
                        scheme = HttpScheme.fromToken("https"),
                        authority = Authority("api.example"),
                        pathAndQuery = "/$id",
                    ),
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    headers = emptyList(),
                ),
            ),
            response = HttpResponseSnapshot(
                head = ResponseHead(
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    status = HttpStatus(200),
                    reasonPhrase = "OK",
                    headers = emptyList(),
                ),
            ),
            state = ExchangeState.COMPLETED,
            startedAtEpochMillis = timestamp,
        )
    }
}
