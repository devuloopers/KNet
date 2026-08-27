package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.application.contract.breakpoint.BreakpointCandidate
import com.devuloopers.knet.application.contract.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.TrafficGeneration
import com.devuloopers.knet.application.contract.traffic.TrafficFacetCounts
import com.devuloopers.knet.application.contract.traffic.TrafficFacetReader
import com.devuloopers.knet.application.contract.traffic.TrafficCaptureSequence
import com.devuloopers.knet.application.contract.traffic.TrafficHistorySequence
import com.devuloopers.knet.application.contract.traffic.TrafficPage
import com.devuloopers.knet.application.contract.traffic.TrafficPageCursor
import com.devuloopers.knet.application.contract.traffic.TrafficPageItem
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.contract.traffic.TrafficSessionCatalog
import com.devuloopers.knet.application.contract.inspection.InspectionAnnotationStore
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectorId
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import com.devuloopers.knet.ui.desktop.traffic.model.SchemeFilter
import com.devuloopers.knet.ui.desktop.traffic.model.HttpVersionFilter
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficInterceptionUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficPagingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `keyset loading retains at most one thousand body-free rows`() = runTest(dispatcher) {
        val port = FakePagedPort(1_250)
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        assertEquals(200, viewModel.uiState.value.transactions.size)
        assertEquals(1_250L, viewModel.uiState.value.totalAvailableCount)
        assertEquals(1_250L, viewModel.uiState.value.transactions.first().sequenceNumber)

        repeat(4) {
            viewModel.processIntent(TrafficIntent.LoadNextPage)
            advanceUntilIdle()
        }

        val firstWindow = viewModel.uiState.value
        assertEquals(1_000, firstWindow.transactions.size)
        assertNotNull(firstWindow.nextPageCursor)

        viewModel.processIntent(TrafficIntent.LoadNextPage)
        advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.LoadNextPage)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1_000, state.transactions.size)
        assertNull(state.nextPageCursor)
        assertEquals("exchange-250", state.transactions.first().transactionId)
        assertEquals("exchange-1249", state.transactions.last().transactionId)
        assertTrue(port.requestedLimits.all { it == 200 })
        assertEquals(1_000, state.transactions.map { it.transactionId }.distinct().size)
    }

    @Test
    fun `newest interception stays on top with the next sequence number`() = runTest(dispatcher) {
        val sessionId = CaptureSessionId("fake-session")
        val port = LiveTrafficPort(
            sessionId = sessionId,
            initialSnapshots = listOf(
                snapshot("first", 1_000L),
                snapshot("second", 2_000L),
                snapshot("third", 3_000L),
            ),
        )
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()

        assertEquals(
            listOf("third", "second", "first"),
            viewModel.uiState.value.transactions.map { it.transactionId },
        )
        assertEquals(listOf(3L, 2L, 1L), viewModel.uiState.value.transactions.map { it.sequenceNumber })

        port.record(snapshot("fourth", 4_000L))
        advanceUntilIdle()

        assertEquals(
            listOf("fourth", "third", "second", "first"),
            viewModel.uiState.value.transactions.map { it.transactionId },
        )
        assertEquals(listOf(4L, 3L, 2L, 1L), viewModel.uiState.value.transactions.map { it.sequenceNumber })

    }

    @Test
    fun `loading a 625 row history preserves the newest identity and exact total`() = runTest(dispatcher) {
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = FakePagedPort(625))
        advanceUntilIdle()

        val newestId = viewModel.uiState.value.transactions.first().transactionId
        assertEquals(625L, viewModel.uiState.value.transactions.first().sequenceNumber)
        assertEquals(625L, viewModel.uiState.value.totalAvailableCount)

        repeat(3) {
            viewModel.processIntent(TrafficIntent.LoadNextPage)
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value
        assertEquals(625, state.transactions.size)
        assertEquals(newestId, state.transactions.first().transactionId)
        assertEquals(625L, state.transactions.first().sequenceNumber)
        assertEquals(1L, state.transactions.last().sequenceNumber)
        assertEquals(625L, state.totalAvailableCount)
    }

    @Test
    fun `empty scheme selection preserves independent scheme and version facets`() = runTest(dispatcher) {
        val sessionId = CaptureSessionId("fake-session")
        val httpsH2 = listOf(
            snapshot("first-h2", 1_000L).withProtocol("HTTP/2"),
            snapshot("second-h2", 2_000L).withProtocol("HTTP/2"),
        )
        val expectedFacets = TrafficFacetCounts(
            totalCount = 2L,
            httpCount = 0L,
            httpsCount = 2L,
        )
        val viewModel = FakeTrafficViewModelFactory.create(
            customTrafficQueryPort = LiveTrafficPort(sessionId, httpsH2, honorFilters = true),
            customTrafficFacetReader = TrafficFacetReader { expectedFacets },
        )
        advanceUntilIdle()

        assertEquals(expectedFacets, viewModel.uiState.value.facetCounts)
        assertEquals(2L, viewModel.uiState.value.totalAvailableCount)

        viewModel.processIntent(TrafficIntent.FilterByScheme(SchemeFilter.HTTP))
        advanceUntilIdle()

        val emptyHttp = viewModel.uiState.value
        assertEquals(0L, emptyHttp.totalAvailableCount)
        assertTrue(emptyHttp.filteredTransactions.isEmpty())
        assertEquals(expectedFacets, emptyHttp.facetCounts)

        viewModel.processIntent(TrafficIntent.FilterByScheme(SchemeFilter.HTTPS))
        viewModel.processIntent(TrafficIntent.FilterByHttpVersion(HttpVersionFilter.HTTP_2))
        advanceUntilIdle()

        assertEquals(2L, viewModel.uiState.value.totalAvailableCount)
        assertEquals(2, viewModel.uiState.value.filteredTransactions.size)
        assertEquals(expectedFacets, viewModel.uiState.value.facetCounts)
    }

    @Test
    fun `continuous generations still refresh to the latest captured exchange`() = runTest(dispatcher) {
        val sessionId = CaptureSessionId("fake-session")
        val port = LiveTrafficPort(sessionId, listOf(snapshot("initial", 1_000L)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()

        repeat(50) { index ->
            port.record(snapshot("live-$index", 2_000L + index))
        }
        advanceUntilIdle()

        assertEquals("live-49", viewModel.uiState.value.transactions.first().transactionId)
        assertTrue(port.queryCalls >= 2)
    }

    @Test
    fun `traffic row renders semantic method while retaining http transport method`() = runTest(dispatcher) {
        val graphQl = snapshot("graphql-row", 2_000L).copy(
            request = HttpRequestSnapshot(
                snapshot("graphql-row", 2_000L).request.head.copy(
                    method = HttpMethod.POST,
                    target = RequestTarget.Absolute(
                        HttpScheme.fromToken("https"),
                        Authority("api.example.com"),
                        "/graphql",
                    ),
                ),
            ),
        )
        val viewModel = FakeTrafficViewModelFactory.create(
            customTrafficQueryPort = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(graphQl)),
        )

        advanceUntilIdle()

        val row = viewModel.uiState.value.transactions.single()
        assertEquals("POST", row.method)
        assertEquals("GQL", row.displayMethod)
        assertEquals(RequestKindId.GRAPHQL, row.requestKind)
    }

    @Test
    fun `persisted semantic annotation reactively refines a nonstandard endpoint`() = runTest(dispatcher) {
        val exchange = snapshot("semantic-hint", 2_000L).copy(
            request = HttpRequestSnapshot(
                snapshot("semantic-hint", 2_000L).request.head.copy(
                    method = HttpMethod.POST,
                    target = RequestTarget.Absolute(
                        HttpScheme.fromToken("https"),
                        Authority("api.example.com"),
                        "/gateway",
                    ),
                ),
            ),
        )
        val annotations = MutableStateFlow<Map<ExchangeId, List<InspectionAnnotation>>>(emptyMap())
        val annotationPort = object : InspectionAnnotationStore {
            override suspend fun put(sessionId: CaptureSessionId, annotation: InspectionAnnotation) = Unit
            override suspend fun get(exchangeId: ExchangeId): List<InspectionAnnotation> =
                annotations.value[exchangeId].orEmpty()

            override fun observe(exchangeId: ExchangeId): Flow<List<InspectionAnnotation>> =
                annotations.map { it[exchangeId].orEmpty() }

            override fun observe(
                exchangeIds: Set<ExchangeId>,
            ): Flow<Map<ExchangeId, List<InspectionAnnotation>>> = annotations.map { current ->
                current.filterKeys { it in exchangeIds }
            }
        }
        val viewModel = FakeTrafficViewModelFactory.create(
            customTrafficQueryPort = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(exchange)),
            customInspectionAnnotationPort = annotationPort,
        )
        advanceUntilIdle()
        assertEquals("POST", viewModel.uiState.value.transactions.single().displayMethod)

        annotations.value = mapOf(
            exchange.id to listOf(
                InspectionAnnotation(
                    exchangeId = exchange.id,
                    inspectorId = InspectorId("graphql"),
                    schemaVersion = 1L,
                    state = InspectionAnnotationState.COMPLETED,
                    document = InspectionDocument(kind = "graphql", title = "GraphQL query"),
                    createdAtEpochMillis = 2_100L,
                ),
            ),
        )
        advanceUntilIdle()

        val refined = viewModel.uiState.value.transactions.single()
        assertEquals("POST", refined.method)
        assertEquals("GQL", refined.displayMethod)
        assertEquals(RequestKindId.GRAPHQL, refined.requestKind)
    }

    @Test
    fun `retained history is visible across sessions before and after the latest session changes`() = runTest(dispatcher) {
        val firstSession = CaptureSessionId("session-1")
        val secondSession = CaptureSessionId("session-2")
        val activeSession = MutableStateFlow<CaptureSessionId?>(firstSession)
        val port = SessionPagedPort(
            mapOf(
                firstSession to listOf(snapshot("old-exchange", 1_000L)),
                secondSession to listOf(snapshot("new-exchange", 2_000L)),
            ),
        )
        val viewModel = FakeTrafficViewModelFactory.create(
            customTrafficQueryPort = port,
            customSessionCatalogPort = object : TrafficSessionCatalog {
                override val latestSessionId: Flow<CaptureSessionId?> = activeSession
            },
        )
        advanceUntilIdle()
        assertEquals(
            listOf("new-exchange", "old-exchange"),
            viewModel.uiState.value.transactions.map { it.transactionId },
        )

        activeSession.value = secondSession
        advanceUntilIdle()

        assertEquals(
            listOf("new-exchange", "old-exchange"),
            viewModel.uiState.value.transactions.map { it.transactionId },
        )
    }

    @Test
    fun `pending breakpoint decorates one in-progress row and survives ordinary filters`() = runTest(dispatcher) {
        val sessionId = CaptureSessionId("fake-session")
        val captured = snapshot("intercepted-exchange", 4_000L)
        val port = LiveTrafficPort(sessionId, listOf(captured))
        val pendingFlow = MutableStateFlow<List<PendingBreakpoint>>(emptyList())
        val viewModel = FakeTrafficViewModelFactory.create(
            customTrafficQueryPort = port,
            pendingBreakpointFlow = pendingFlow,
        )
        advanceUntilIdle()

        pendingFlow.value = listOf(
            PendingBreakpoint(
                id = "pending-request",
                ruleId = "rule-request",
                candidate = BreakpointCandidate(
                    exchangeId = captured.id,
                    phase = BreakpointPhase.REQUEST,
                    request = captured.request,
                    startedAtEpochMillis = captured.startedAtEpochMillis,
                ),
            ),
        )
        advanceUntilIdle()

        val paused = viewModel.uiState.value.transactions.single()
        assertEquals("intercepted-exchange", paused.transactionId)
        assertEquals(0, paused.status)
        assertEquals("In Progress", paused.statusText)
        assertTrue(paused.interception is TrafficInterceptionUiState.Paused)

        viewModel.processIntent(TrafficIntent.FilterByStatus(com.devuloopers.knet.ui.desktop.traffic.model.StatusFilter.STATUS_2XX))
        advanceUntilIdle()
        assertEquals(
            listOf("intercepted-exchange"),
            viewModel.uiState.value.filteredTransactions.map { row -> row.transactionId },
        )

        pendingFlow.value = emptyList()
        advanceUntilIdle()

        val completedProjection = viewModel.uiState.value.transactions.single()
        assertEquals("intercepted-exchange", completedProjection.transactionId)
        assertTrue(completedProjection.interception is TrafficInterceptionUiState.Matched)
    }

    @Test
    fun `response breakpoint remains in progress until its decision is resolved`() = runTest(dispatcher) {
        val sessionId = CaptureSessionId("fake-session")
        val requestOnly = snapshot("response-interception", 5_000L)
        val response = HttpResponseSnapshot(
            ResponseHead(
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                status = HttpStatus(204),
                reasonPhrase = "No Content",
                headers = emptyList(),
            ),
        )
        val captured = requestOnly.copy(response = response, state = ExchangeState.COMPLETED)
        val pendingFlow = MutableStateFlow<List<PendingBreakpoint>>(emptyList())
        val viewModel = FakeTrafficViewModelFactory.create(
            customTrafficQueryPort = LiveTrafficPort(sessionId, listOf(captured)),
            pendingBreakpointFlow = pendingFlow,
        )
        advanceUntilIdle()

        pendingFlow.value = listOf(
            PendingBreakpoint(
                id = "pending-response",
                ruleId = "rule-response",
                candidate = BreakpointCandidate(
                    exchangeId = captured.id,
                    phase = BreakpointPhase.RESPONSE,
                    request = captured.request,
                    response = response,
                    startedAtEpochMillis = captured.startedAtEpochMillis,
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.transactions.single().status)
        assertEquals("In Progress", viewModel.uiState.value.transactions.single().statusText)

        pendingFlow.value = emptyList()
        advanceUntilIdle()

        val resolved = viewModel.uiState.value.transactions.single()
        assertEquals(204, resolved.status)
        assertTrue(resolved.interception is TrafficInterceptionUiState.Matched)
    }

    @Test
    fun `captured row opens one application-prepared breakpoint draft`() = runTest(dispatcher) {
        val captured = snapshot("draft-source", 6_000L)
        val viewModel = FakeTrafficViewModelFactory.create(
            customTrafficQueryPort = LiveTrafficPort(
                sessionId = CaptureSessionId("fake-session"),
                initialSnapshots = listOf(captured),
            ),
        )
        advanceUntilIdle()

        viewModel.createBreakpointFromTransaction(captured.id.value)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isBreakpointDrawerVisible)
        assertEquals("https://api.example.com/items/draft-source", state.prefilledBreakpointRule?.urlPattern)
        assertEquals(
            ProtocolMatchCriteria.HttpDefault,
            state.prefilledBreakpointRule?.protocolCriteria,
        )
        assertEquals(emptyList(), state.prefilledBreakpointProtocolValues)
    }

    private class FakePagedPort(rowCount: Int) : TrafficQuery {
        private val snapshots = List(rowCount) { index -> snapshot("exchange-$index", 10_000L - index) }
        val requestedLimits = mutableListOf<Int>()
        override val generations: Flow<TrafficGeneration> = emptyFlow()

        override suspend fun query(query: TrafficPageQuery): TrafficPage {
            requestedLimits += query.limit
            val offset = query.cursor?.value?.toInt() ?: 0
            val page = snapshots.drop(offset).take(query.limit)
            val nextOffset = offset + page.size
            return TrafficPage(
                items = page.mapIndexed { index, snapshot ->
                    val historySequence = (snapshots.size - offset - index).toLong()
                    TrafficPageItem(
                        captureSequence = TrafficCaptureSequence(STORAGE_SEQUENCE_OFFSET + historySequence),
                        historySequence = TrafficHistorySequence(historySequence),
                        exchange = snapshot,
                    )
                },
                nextCursor = nextOffset.takeIf { it < snapshots.size }?.let { TrafficPageCursor(it.toString()) },
                totalCount = snapshots.size.toLong(),
                generation = 1L,
            )
        }

        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? =
            snapshots.firstOrNull { it.id == exchangeId }

        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
            error("Paging rows must not read bodies.")

    }

    private class LiveTrafficPort(
        private val sessionId: CaptureSessionId,
        initialSnapshots: List<HttpExchangeSnapshot>,
        private val honorFilters: Boolean = false,
    ) : TrafficQuery {
        private val mutableGenerations = MutableSharedFlow<TrafficGeneration>(extraBufferCapacity = 64)
        private val snapshots = initialSnapshots.toMutableList()
        private var generation = 1L
        var queryCalls: Int = 0

        override val generations: Flow<TrafficGeneration> = mutableGenerations

        override suspend fun query(query: TrafficPageQuery): TrafficPage {
            queryCalls += 1
            val sequenceById = snapshots.mapIndexed { index, snapshot ->
                snapshot.id to TrafficCaptureSequence(index + 1L)
            }.toMap()
            val candidates = if (honorFilters) snapshots.asSequence()
                .filter { snapshot -> query.schemes.isEmpty() || snapshot.request.head.target.schemeOrNull() in query.schemes }
                .filter { snapshot -> query.protocols.isEmpty() || snapshot.request.head.protocol in query.protocols || snapshot.response?.head?.protocol in query.protocols }
                .filter { snapshot -> query.methods.isEmpty() || snapshot.request.head.method in query.methods }
                .filter { snapshot -> query.statuses.isEmpty() || snapshot.response?.head?.status in query.statuses }
                .toList()
            else snapshots
            val orderedSnapshots = candidates.asSequence()
                .sortedByDescending { snapshot -> snapshot.startedAtEpochMillis }
                .toList()
            return TrafficPage(
                items = orderedSnapshots.map { snapshot ->
                    val sequence = sequenceById.getValue(snapshot.id)
                    TrafficPageItem(
                        captureSequence = sequence,
                        historySequence = TrafficHistorySequence(sequence.value),
                        exchange = snapshot,
                    )
                },
                nextCursor = null,
                totalCount = candidates.size.toLong(),
                generation = generation,
            )
        }

        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? =
            snapshots.firstOrNull { it.id == exchangeId }

        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
            error("Live traffic rows must not read bodies.")

        fun record(snapshot: HttpExchangeSnapshot) {
            snapshots += snapshot
            generation++
            check(mutableGenerations.tryEmit(TrafficGeneration(sessionId, generation)))
        }
    }

    private class SessionPagedPort(
        private val snapshotsBySession: Map<CaptureSessionId, List<HttpExchangeSnapshot>>,
    ) : TrafficQuery {
        override val generations: Flow<TrafficGeneration> = emptyFlow()

        override suspend fun query(query: TrafficPageQuery): TrafficPage {
            val snapshots = query.sessionId
                ?.let { sessionId -> snapshotsBySession[sessionId].orEmpty() }
                ?: snapshotsBySession.values.flatten().sortedByDescending { it.startedAtEpochMillis }
            return TrafficPage(
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
        }

        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? =
            snapshotsBySession.values.flatten().firstOrNull { it.id == exchangeId }

        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
            error("Session paging rows must not read bodies.")
    }

    private companion object {
        const val STORAGE_SEQUENCE_OFFSET: Long = 10_000L

        fun snapshot(id: String, timestamp: Long): HttpExchangeSnapshot = HttpExchangeSnapshot(
            id = ExchangeId(id),
            request = HttpRequestSnapshot(
                RequestHead(
                    method = HttpMethod.fromToken("GET"),
                    target = RequestTarget.Absolute(
                        HttpScheme.fromToken("https"),
                        Authority("api.example.com"),
                        "/items/$id",
                    ),
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    headers = emptyList(),
                ),
            ),
            state = ExchangeState.COMPLETED,
            startedAtEpochMillis = timestamp,
        )

        fun HttpExchangeSnapshot.withProtocol(token: String): HttpExchangeSnapshot = copy(
            request = HttpRequestSnapshot(
                request.head.copy(protocol = ApplicationProtocol.fromToken(token)),
                request.body,
                request.trailers,
            ),
        )

        fun RequestTarget.schemeOrNull(): HttpScheme? = (this as? RequestTarget.Absolute)?.scheme
    }
}
