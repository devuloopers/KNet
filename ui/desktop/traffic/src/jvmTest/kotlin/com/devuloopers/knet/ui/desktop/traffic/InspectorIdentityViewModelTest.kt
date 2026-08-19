package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.TrafficGeneration
import com.devuloopers.knet.application.port.traffic.TrafficPage
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
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
        advanceUntilIdle()

        assertEquals(1, port.detailReads.getValue("newest"))
        assertEquals("", viewModel.uiState.value.preparedState.requestBodyText)
        assertEquals("", viewModel.uiState.value.preparedState.responseBodyText)
    }

    private class DetailPort(
        private val snapshots: List<HttpExchangeSnapshot>,
        private val delayedExchangeId: String? = null,
    ) : TrafficQueryPort {
        override val generations: Flow<TrafficGeneration> = emptyFlow()
        val detailReads = mutableMapOf<String, Int>()

        override suspend fun query(query: TrafficPageQuery): TrafficPage = TrafficPage(
            items = snapshots,
            nextCursor = null,
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
