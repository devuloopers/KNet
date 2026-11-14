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
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.application.contract.traffic.TrafficMaintenance
import com.devuloopers.knet.identity.RegisteredDeviceId
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
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
    fun `successful clear resets facet pills before the authoritative reload completes`() = runTest(dispatcher) {
        val initialFacets = TrafficFacetCounts(totalCount = 3L, httpCount = 1L, httpsCount = 2L)
        val reloadStarted = CompletableDeferred<Unit>()
        val allowReload = CompletableDeferred<Unit>()
        var facetQueryCount = 0
        val viewModel = FakeTrafficViewModelFactory.create(
            customTrafficFacetReader = TrafficFacetReader {
                facetQueryCount += 1
                if (facetQueryCount > 1) {
                    reloadStarted.complete(Unit)
                    allowReload.await()
                    TrafficFacetCounts()
                } else {
                    initialFacets
                }
            },
        )
        advanceUntilIdle()
        assertEquals(initialFacets, viewModel.uiState.value.facetCounts)

        viewModel.processIntent(TrafficIntent.ClearFeed)
        runCurrent()

        assertTrue(reloadStarted.isCompleted)
        assertEquals(TrafficFacetCounts(), viewModel.uiState.value.facetCounts)

        allowReload.complete(Unit)
        advanceUntilIdle()
        assertEquals(TrafficFacetCounts(), viewModel.uiState.value.facetCounts)
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

    @Test fun `paused source resolves after asynchronous capture admission becomes durable`() = runTest(dispatcher) {
        val source = com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(com.devuloopers.knet.identity.RegisteredDeviceId("paired"))
        val captured = snapshot("delayed", 4000).copy(connectionId = com.devuloopers.knet.traffic.id.ConnectionId("connection"))
        val changes = MutableSharedFlow<TrafficGeneration>(extraBufferCapacity = 8)
        var durable = false
        val base = FakePagedPort(0)
        val port = object : TrafficQuery by base {
            override val generations = changes
            override suspend fun getExchange(exchangeId: ExchangeId) = captured.takeIf { durable && it.id == exchangeId }
            override suspend fun getConnectionIngress(connectionId: com.devuloopers.knet.traffic.id.ConnectionId) =
                com.devuloopers.knet.application.contract.traffic.TrafficConnectionIngress(
                    com.devuloopers.knet.traffic.model.IngressContext(com.devuloopers.knet.traffic.model.IngressKind.CompanionDirect,
                        com.devuloopers.knet.traffic.model.ClientIdentity("paired")), "127.0.0.1")
        }
        val pending = MutableStateFlow<List<PendingBreakpoint>>(emptyList())
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port, pendingBreakpointFlow = pending)
        advanceUntilIdle(); viewModel.selectSource(source); advanceUntilIdle()
        pending.value = listOf(PendingBreakpoint("pending", "rule", BreakpointCandidate(captured.id, BreakpointPhase.REQUEST,
            captured.request, startedAtEpochMillis = captured.startedAtEpochMillis)))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.transactions.isEmpty(), "Unverified ownership cannot leak into a selected device")
        durable = true
        changes.emit(TrafficGeneration(CaptureSessionId("fake-session"), 2))
        advanceUntilIdle()
        assertEquals("delayed", viewModel.uiState.value.transactions.single().transactionId)
        assertTrue(viewModel.uiState.value.transactions.single().interception is TrafficInterceptionUiState.Paused)
    }

    @Test
    fun `device workspaces restore filters selection and a departing viewport without changing capture`() = runTest(dispatcher) {
        val sourceA = com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(com.devuloopers.knet.identity.RegisteredDeviceId("a"))
        val sourceB = com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(com.devuloopers.knet.identity.RegisteredDeviceId("b"))
        val base = FakePagedPort(1)
        val seen = mutableListOf<TrafficPageQuery>()
        val port = object : TrafficQuery by base {
            override suspend fun query(query: TrafficPageQuery): TrafficPage {
                seen += query
                return base.query(query.copy(cursor = null)).copy(items = listOf(TrafficPageItem(
                    captureSequence = TrafficCaptureSequence(1), historySequence = TrafficHistorySequence(1), exchange = snapshot(query.source.preferenceKey, 10))))
            }
        }
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        viewModel.selectSource(sourceA); advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.Search("GET")); advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.SelectTransaction(sourceA.preferenceKey)); advanceUntilIdle()
        viewModel.selectSource(sourceB)
        viewModel.retainViewport(sourceA, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(7, 11, 22))
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(sourceB.preferenceKey, viewModel.uiState.value.transactions.single().transactionId)
        viewModel.selectSource(sourceA); advanceUntilIdle()
        assertEquals("GET", viewModel.uiState.value.searchQuery)
        assertEquals(sourceA.preferenceKey, viewModel.uiState.value.selectedTransactionId)
        assertEquals(7, viewModel.uiState.value.viewport.row)
        assertEquals(22, viewModel.uiState.value.viewport.horizontal)
        assertTrue(seen.filter { it.source == sourceA }.any { it.searchContains == "GET" })
        assertTrue(seen.any { it.source == sourceB })
    }

    @Test
    fun `clear retains its original source while switching cannot erase the new workspace`() = runTest(dispatcher) {
        val sourceA = com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(com.devuloopers.knet.identity.RegisteredDeviceId("a"))
        val sourceB = com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(com.devuloopers.knet.identity.RegisteredDeviceId("b"))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var cleared: com.devuloopers.knet.application.contract.traffic.TrafficSource? = null
        val maintenance = object : com.devuloopers.knet.application.contract.traffic.TrafficMaintenance {
            override suspend fun clearTerminalTraffic() = error("Scoped clear must not call global deletion")
            override suspend fun clearSourceTraffic(source: com.devuloopers.knet.application.contract.traffic.TrafficSource) {
                cleared = source; entered.complete(Unit); release.await()
            }
        }
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = FakePagedPort(2), customTrafficMaintenance = maintenance)
        advanceUntilIdle()
        viewModel.selectSource(sourceA); advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.ClearFeed)
        runCurrent(); entered.await()
        viewModel.selectSource(sourceB); runCurrent()
        release.complete(Unit); advanceUntilIdle()
        assertEquals(sourceA, cleared)
        assertEquals(sourceB, viewModel.uiState.value.source)
        assertEquals(2, viewModel.uiState.value.transactions.size)
    }

    @Test
    fun `background Close clears only its explicit source despite filters and duplicate clicks`() = runTest(dispatcher) {
        val sourceA = TrafficSource.Device(RegisteredDeviceId("a"))
        val sourceB = TrafficSource.Device(RegisteredDeviceId("b"))
        val rows = mutableMapOf<TrafficSource, List<HttpExchangeSnapshot>>(sourceA to listOf(snapshot("a", 1)), sourceB to listOf(snapshot("b", 2)))
        val queriedSources = mutableListOf<TrafficSource>()
        val port = object : TrafficQuery by FakePagedPort(0) {
            override suspend fun query(query: TrafficPageQuery): TrafficPage {
                queriedSources += query.source
                val matching = rows[query.source].orEmpty().filter { query.searchContains.isNullOrEmpty() || it.id.value.contains(query.searchContains!!) }
                return TrafficPage(matching.map { TrafficPageItem(TrafficCaptureSequence(1), TrafficHistorySequence(1), it) },
                    nextCursor = null, totalCount = matching.size.toLong(), generation = 1)
            }
        }
        val release = CompletableDeferred<Unit>()
        val clears = mutableListOf<TrafficSource>()
        val maintenance = object : TrafficMaintenance {
            override suspend fun clearTerminalTraffic() = error("Never clear globally")
            override suspend fun clearSourceTraffic(source: TrafficSource) {
                clears += source; release.await(); rows.remove(source)
            }
        }
        val owner = androidx.lifecycle.ViewModelStore()
        try {
            val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port, customTrafficMaintenance = maintenance)
            owner.put("traffic", viewModel)
            advanceUntilIdle()
            viewModel.selectSource(sourceA); advanceUntilIdle()
            viewModel.processIntent(TrafficIntent.Search("no-match")); advanceUntilIdle()
            assertTrue(viewModel.uiState.value.transactions.isEmpty())
            viewModel.selectSource(sourceB); advanceUntilIdle()
            assertEquals("b", viewModel.uiState.value.transactions.single().transactionId)
            val siblingQueryCount = queriedSources.count { it == sourceB }
            viewModel.clearSource(sourceA)
            viewModel.clearSource(sourceA)
            runCurrent()
            assertEquals(listOf<TrafficSource>(sourceA), clears)
            release.complete(Unit); advanceUntilIdle()
            assertEquals(sourceB, viewModel.uiState.value.source)
            assertEquals("b", viewModel.uiState.value.transactions.single().transactionId)
            assertEquals(siblingQueryCount, queriedSources.count { it == sourceB }, "Background Close must not replace the current row window")
            viewModel.selectSource(sourceA); advanceUntilIdle()
            assertTrue(viewModel.uiState.value.transactions.isEmpty(), "Closed tab must not restore stale cached rows")
            assertEquals("", viewModel.uiState.value.searchQuery, "The closed background workspace cache is discarded")
            assertEquals(listOf<TrafficSource>(sourceA), clears)
        } finally { owner.clear() }
    }

    @Test
    fun `failed source deletion preserves displayed traffic and allows retry`() = runTest(dispatcher) {
        var fail = true
        val maintenance = object : TrafficMaintenance {
            override suspend fun clearTerminalTraffic() { if (fail) error("Storage unavailable") }
        }
        val owner = androidx.lifecycle.ViewModelStore()
        try {
            val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = FakePagedPort(2), customTrafficMaintenance = maintenance)
            owner.put("traffic", viewModel)
            advanceUntilIdle()
            viewModel.clearSource(viewModel.uiState.value.source)
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.transactions.size)
            assertNotNull(viewModel.uiState.value.trafficErrorMessage)
            assertEquals(false, viewModel.uiState.value.isClearingHistory)
            fail = false
            viewModel.clearSource(viewModel.uiState.value.source)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.trafficErrorMessage)
            assertEquals(false, viewModel.uiState.value.isClearingHistory)
        } finally { owner.clear() }
    }

    @Test
    fun `clear coalesces batch invalidations and refreshes replacement traffic once`() = runTest(dispatcher) {
        val changes = MutableSharedFlow<TrafficGeneration>(extraBufferCapacity = 2_048)
        var rows = listOf(snapshot("before-clear", 1))
        var reads = 0
        val port = object : TrafficQuery by FakePagedPort(0) {
            override val generations: Flow<TrafficGeneration> = changes
            override suspend fun query(query: TrafficPageQuery): TrafficPage {
                reads++
                return TrafficPage(rows.map { TrafficPageItem(TrafficCaptureSequence(1), TrafficHistorySequence(1), it) },
                    nextCursor = null, totalCount = rows.size.toLong(), generation = 0)
            }
        }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val maintenance = object : TrafficMaintenance {
            override suspend fun clearTerminalTraffic() { entered.complete(Unit); release.await(); rows = listOf(snapshot("new-live", 2)) }
        }
        val owner = androidx.lifecycle.ViewModelStore()
        try {
            val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port, customTrafficMaintenance = maintenance)
            owner.put("traffic", viewModel)
            advanceUntilIdle()
            val before = reads
            viewModel.processIntent(TrafficIntent.ClearFeed)
            runCurrent(); entered.await()
            repeat(20) { index -> changes.emit(TrafficGeneration(CaptureSessionId("fake-session"), index.toLong() + 2)) }
            advanceUntilIdle()
            assertEquals(before, reads, "Clear batch invalidations must not requery disappearing history")
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(before + 1, reads)
            assertEquals("new-live", viewModel.uiState.value.transactions.single().transactionId)
            assertEquals(false, viewModel.uiState.value.isClearingHistory)
        } finally { owner.clear() }
    }

    @Test
    fun `failed clear refreshes counts when capture rotation already changed the catalog`() = runTest(dispatcher) {
        val latest = MutableStateFlow<CaptureSessionId?>(CaptureSessionId("fake-session"))
        val catalog = object : TrafficSessionCatalog { override val latestSessionId: Flow<CaptureSessionId?> = latest }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val maintenance = object : TrafficMaintenance {
            override suspend fun clearTerminalTraffic() {
                latest.value = CaptureSessionId("replacement")
                entered.complete(Unit)
                release.await()
                error("Synthetic metadata failure after rotation")
            }
        }
        val owner = androidx.lifecycle.ViewModelStore()
        try {
            val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = FakePagedPort(2),
                customSessionCatalogPort = catalog, customTrafficMaintenance = maintenance)
            owner.put("traffic", viewModel)
            advanceUntilIdle()
            viewModel.processIntent(TrafficIntent.ClearFeed)
            runCurrent(); entered.await(); runCurrent()
            assertEquals(CaptureSessionId("replacement"), viewModel.uiState.value.sessionId)
            release.complete(Unit); advanceUntilIdle()
            assertEquals(2L, viewModel.uiState.value.totalAvailableCount)
            assertEquals(2, viewModel.uiState.value.transactions.size)
            assertNotNull(viewModel.uiState.value.trafficErrorMessage)
            assertEquals(false, viewModel.uiState.value.isClearingHistory)
        } finally { owner.clear() }
    }

    @Test
    fun `stored manual off is applied before later arrivals and rapid controls persist the final selection`() = runTest(dispatcher) {
        val manual = com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode.MANUAL
        val smart = com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode.SMART
        val saved = MutableStateFlow(com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings(
            trafficAutoScroll = com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollSettings(manual, false)))
        val preferences = object : com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository {
            override val settingsFlow = saved
            override suspend fun updateSettings(transform: (com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings) -> com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings) {
                saved.value = transform(saved.value)
            }
        }
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old", 1_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port, customWorkspacePreferencesRepository = preferences)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.autoScroll)
        port.record(snapshot("new", 2_000)); advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.reading.newCount)
        assertEquals(listOf("old"), viewModel.uiState.value.transactions.map { it.transactionId })
        repeat(10) { viewModel.processIntent(TrafficIntent.SetAutoScrollMode(smart)); viewModel.processIntent(TrafficIntent.SetAutoScrollMode(manual)) }
        viewModel.processIntent(TrafficIntent.ToggleAutoScroll)
        advanceUntilIdle()
        assertEquals(manual, saved.value.trafficAutoScroll.mode)
        assertTrue(saved.value.trafficAutoScroll.manualEnabled)
        assertEquals(viewModel.uiState.value.scrollSettings, saved.value.trafficAutoScroll)
    }

    @Test
    fun `pausing during an in flight refresh keeps the old window and counts the new row`() = runTest(dispatcher) {
        val delegate = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old", 1_000)))
        var gate: CompletableDeferred<Unit>? = null
        val port = object : TrafficQuery by delegate {
            override suspend fun query(query: TrafficPageQuery): TrafficPage {
                val result = delegate.query(query)
                gate?.await()
                return result
            }
        }
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        gate = CompletableDeferred()
        delegate.record(snapshot("new", 2_000))
        testScheduler.advanceTimeBy(100)
        runCurrent()
        assertTrue(viewModel.uiState.value.isPageLoading)
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 7), userScrolled = true)
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("old"), viewModel.uiState.value.transactions.map { it.transactionId })
        assertEquals(1L, viewModel.uiState.value.reading.newCount)
        assertEquals(false, viewModel.uiState.value.autoScroll)
    }

    @Test
    fun `smart pauses at a partial first row counts arrivals and preserves selection and window`() = runTest(dispatcher) {
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("first", 1_000), snapshot("second", 2_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.SelectTransaction("first"))
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 5), userScrolled = true)
        assertEquals(false, viewModel.uiState.value.autoScroll)
        port.record(snapshot("third", 3_000)); advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.reading.newCount)
        assertEquals(listOf("second", "first"), viewModel.uiState.value.transactions.map { it.transactionId })
        assertEquals("first", viewModel.uiState.value.selectedTransactionId)
        // Lifecycle-only invalidations must never increment the insertion counter.
        port.invalidate(); advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.reading.newCount)
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(), userScrolled = true)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.autoScroll)
        viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
        viewModel.observeVisibleRows(TrafficSource.All, viewModel.uiState.value.readingEpoch,
            com.devuloopers.knet.ui.desktop.traffic.model.TrafficVisibleRows(listOf("third"), true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.autoScroll)
        assertEquals(0L, viewModel.uiState.value.reading.newCount)
        assertEquals("third", viewModel.uiState.value.transactions.first().transactionId)
    }

    @Test
    fun `manual off survives a one time jump and mode changes preserve the manual switch`() = runTest(dispatcher) {
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("first", 1_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        val manual = com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode.MANUAL
        val smart = com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode.SMART
        viewModel.processIntent(TrafficIntent.SetAutoScrollMode(manual))
        viewModel.processIntent(TrafficIntent.ToggleAutoScroll); advanceUntilIdle()
        port.record(snapshot("second", 2_000)); advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.reading.newCount)
        viewModel.processIntent(TrafficIntent.JumpToLatest); advanceUntilIdle()
        assertEquals("second", viewModel.uiState.value.transactions.first().transactionId)
        assertEquals(0L, viewModel.uiState.value.reading.newCount)
        assertEquals(false, viewModel.uiState.value.autoScroll)
        port.record(snapshot("third", 3_000)); advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.reading.newCount)
        viewModel.processIntent(TrafficIntent.SetAutoScrollMode(smart)); advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.autoScroll)
        viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
        reportVisible(viewModel, listOf("third"), true); advanceUntilIdle()
        assertTrue(viewModel.uiState.value.autoScroll)
        viewModel.processIntent(TrafficIntent.SetAutoScrollMode(manual)); advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.autoScroll)
        viewModel.processIntent(TrafficIntent.ToggleAutoScroll); advanceUntilIdle()
        assertTrue(viewModel.uiState.value.autoScroll)
        assertEquals("third", viewModel.uiState.value.transactions.first().transactionId)
        assertEquals(0L, viewModel.uiState.value.reading.newCount)
    }

    @Test
    fun `history window and exact count survive a burst larger than the row memory limit`() = runTest(dispatcher) {
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("anchor", 1_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 1), userScrolled = true)
        repeat(1_500) { port.record(snapshot("burst-$it", 2_000L + it)) }
        advanceUntilIdle()
        assertEquals(1_500L, viewModel.uiState.value.reading.newCount)
        assertEquals(listOf("anchor"), viewModel.uiState.value.transactions.map { it.transactionId })
        assertTrue(port.queryCalls < 10, "A burst must coalesce rather than query per arrival")
        viewModel.processIntent(TrafficIntent.JumpToLatest); advanceUntilIdle()
        assertEquals("burst-1499", viewModel.uiState.value.transactions.first().transactionId)
        assertEquals(0L, viewModel.uiState.value.reading.newCount)
    }

    @Test
    fun `filter changes rebase unread arrivals and clear resets only the selected source`() = runTest(dispatcher) {
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old", 1_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 1), userScrolled = true)
        port.record(snapshot("new", 2_000)); advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.reading.newCount)
        viewModel.processIntent(TrafficIntent.Search("new")); advanceUntilIdle()
        assertEquals(0L, viewModel.uiState.value.reading.newCount)
        assertTrue(viewModel.uiState.value.autoScroll)
        viewModel.clearSource(TrafficSource.All); advanceUntilIdle()
        assertEquals(0L, viewModel.uiState.value.reading.newCount)
        assertEquals(com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(), viewModel.uiState.value.viewport)
    }

    @Test
    fun `source readings remain independent and survive row cache eviction`() = runTest(dispatcher) {
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old", 1_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        val sourceA = TrafficSource.Device(RegisteredDeviceId("a"))
        viewModel.selectSource(sourceA); advanceUntilIdle()
        viewModel.retainViewport(sourceA, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 3), userScrolled = true)
        for (id in listOf("b", "c", "d", "e")) { viewModel.selectSource(TrafficSource.Device(RegisteredDeviceId(id))); advanceUntilIdle() }
        assertTrue(viewModel.uiState.value.autoScroll)
        port.record(snapshot("new", 2_000)); advanceUntilIdle()
        viewModel.selectSource(sourceA); advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.autoScroll)
        assertEquals(1L, viewModel.uiState.value.reading.newCount)
        assertEquals(listOf("old"), viewModel.uiState.value.transactions.map { it.transactionId })
    }

    @Test
    fun `nineteen arrivals decrease with last seen rows and old window top never acknowledges the batch`() = runTest(dispatcher) {
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old-100", 100_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 1), true)
        for (number in 101..119) port.record(snapshot("new-$number", number * 1_000L))
        advanceUntilIdle()
        assertEquals(19L, viewModel.uiState.value.reading.newCount)
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(), true)
        reportVisible(viewModel, listOf("old-100"), true); advanceUntilIdle()
        assertEquals(19L, viewModel.uiState.value.reading.newCount)
        assertEquals(false, viewModel.uiState.value.autoScroll)
        assertEquals(listOf("old-100"), viewModel.uiState.value.transactions.map { it.transactionId })
        viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
        assertEquals(19L, viewModel.uiState.value.reading.newCount)
        assertEquals(false, viewModel.uiState.value.autoScroll, "Loading newer data must not force a jump")
        reportVisible(viewModel, listOf("new-101")); advanceUntilIdle()
        assertEquals(18L, viewModel.uiState.value.reading.newCount)
        reportVisible(viewModel, listOf("new-102", "new-101")); advanceUntilIdle()
        assertEquals(17L, viewModel.uiState.value.reading.newCount)
        val calls = port.queryCalls
        reportVisible(viewModel, listOf("new-101")); reportVisible(viewModel, listOf("new-102")); advanceUntilIdle()
        assertEquals(calls, port.queryCalls, "Repeated and older visibility cannot reread or move the checkpoint backwards")
        viewModel.processIntent(TrafficIntent.SelectTransaction("new-119")); advanceUntilIdle()
        assertEquals(17L, viewModel.uiState.value.reading.newCount, "Selection alone is not visibility")
        reportVisible(viewModel, listOf("new-119", "new-118"), true); advanceUntilIdle()
        assertEquals(0L, viewModel.uiState.value.reading.newCount)
        assertTrue(viewModel.uiState.value.autoScroll)
        assertNull(viewModel.uiState.value.reading.seenThrough)
    }

    @Test
    fun `jump to latest clears a partially read batch while manual remains off`() = runTest(dispatcher) {
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old", 1_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.SetAutoScrollMode(com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode.MANUAL))
        viewModel.processIntent(TrafficIntent.ToggleAutoScroll); advanceUntilIdle()
        repeat(19) { port.record(snapshot("new-$it", 2_000L + it)) }; advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
        reportVisible(viewModel, listOf("new-0")); advanceUntilIdle()
        assertEquals(18L, viewModel.uiState.value.reading.newCount)
        viewModel.processIntent(TrafficIntent.JumpToLatest); advanceUntilIdle()
        assertEquals(0L, viewModel.uiState.value.reading.newCount)
        assertNull(viewModel.uiState.value.reading.seenThrough)
        assertEquals(false, viewModel.uiState.value.autoScroll)
        port.record(snapshot("after-jump", 3_000)); advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.reading.newCount)
        assertEquals(false, viewModel.uiState.value.reading.atTop, "An old loaded top is no longer the actual live edge")
    }

    @Test
    fun `fifteen hundred newer arrivals page adjacently within the row bound and older eviction has no paging gap`() = runTest(dispatcher) {
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old", 1_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 1), true)
        repeat(1_500) { port.record(snapshot("burst-$it", 2_000L + it)) }; advanceUntilIdle()
        val loaded = mutableSetOf<String>()
        repeat(8) { block ->
            viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
            val rows = viewModel.uiState.value.transactions
            assertTrue(rows.size <= 1_000)
            loaded += rows.map { it.transactionId }
            val lastInPage = minOf((block + 1) * 200 - 1, 1_499)
            assertEquals("burst-$lastInPage", rows.first().transactionId, "Newer paging must load the adjacent block")
            reportVisible(viewModel, listOf("burst-${block * 200}")); advanceUntilIdle()
            assertEquals(1_499L - block * 200, viewModel.uiState.value.reading.newCount)
            assertEquals(false, viewModel.uiState.value.autoScroll)
        }
        assertEquals((0 until 1_500).map { "burst-$it" }.toSet() + "old", loaded)
        reportVisible(viewModel, listOf("burst-1499"), true); advanceUntilIdle()
        assertEquals(0L, viewModel.uiState.value.reading.newCount)
        assertTrue(viewModel.uiState.value.autoScroll)
        assertNotNull(viewModel.uiState.value.olderPageAnchor)
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(990, 0), true)
        viewModel.processIntent(TrafficIntent.LoadNextPage); advanceUntilIdle()
        assertEquals("burst-300", viewModel.uiState.value.transactions.last().transactionId)
        assertEquals("burst-1299", viewModel.uiState.value.transactions.first().transactionId)
        assertTrue(viewModel.uiState.value.hasNewerTraffic)
        viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
        assertEquals("burst-1499", viewModel.uiState.value.transactions.first().transactionId)
    }

    @Test
    fun `stale progress query cannot overwrite a newer visible checkpoint`() = runTest(dispatcher) {
        val delegate = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old", 1_000)))
        var gate: CompletableDeferred<Unit>? = null
        val port = object : TrafficQuery by delegate {
            override suspend fun query(query: TrafficPageQuery): TrafficPage {
                val result = delegate.query(query)
                gate?.await()
                return result
            }
        }
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 1), true)
        repeat(19) { delegate.record(snapshot("new-$it", 2_000L + it)) }; advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
        gate = CompletableDeferred()
        reportVisible(viewModel, listOf("new-0"))
        testScheduler.advanceTimeBy(100); runCurrent()
        assertTrue(viewModel.uiState.value.isPageLoading)
        reportVisible(viewModel, listOf("new-1"))
        gate!!.complete(Unit); gate = null; advanceUntilIdle()
        assertEquals(17L, viewModel.uiState.value.reading.newCount)
        assertEquals("new-1", (viewModel.uiState.value.reading.seenThrough!!.position.recordId as
            com.devuloopers.knet.application.contract.traffic.TrafficRecordId.Exchange).id.value)
    }

    @Test
    fun `source filter and clear replacement reject stale visibility callbacks`() = runTest(dispatcher) {
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old", 1_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        val sourceA = TrafficSource.Device(RegisteredDeviceId("progress-a"))
        viewModel.selectSource(sourceA); advanceUntilIdle()
        viewModel.retainViewport(sourceA, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 1), true)
        repeat(19) { port.record(snapshot("new-$it", 2_000L + it)) }; advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
        val oldEpoch = viewModel.uiState.value.readingEpoch
        val sourceB = TrafficSource.Device(RegisteredDeviceId("progress-b"))
        viewModel.selectSource(sourceB); advanceUntilIdle()
        viewModel.observeVisibleRows(sourceA, oldEpoch, com.devuloopers.knet.ui.desktop.traffic.model.TrafficVisibleRows(listOf("new-18"), true))
        viewModel.selectSource(sourceA); advanceUntilIdle()
        assertEquals(19L, viewModel.uiState.value.reading.newCount)
        reportVisible(viewModel, listOf("new-0")); advanceUntilIdle()
        assertEquals(18L, viewModel.uiState.value.reading.newCount)
        val beforeFilter = viewModel.uiState.value.readingEpoch
        viewModel.processIntent(TrafficIntent.Search("new")); advanceUntilIdle()
        viewModel.observeVisibleRows(sourceA, beforeFilter, com.devuloopers.knet.ui.desktop.traffic.model.TrafficVisibleRows(listOf("new-18"), true))
        assertNull(viewModel.uiState.value.reading.seenThrough)
        val beforeClear = viewModel.uiState.value.readingEpoch
        viewModel.clearSource(sourceA); advanceUntilIdle()
        viewModel.observeVisibleRows(sourceA, beforeClear, com.devuloopers.knet.ui.desktop.traffic.model.TrafficVisibleRows(listOf("new-18"), true))
        assertEquals(0L, viewModel.uiState.value.reading.newCount)
        assertNull(viewModel.uiState.value.reading.seenThrough)
    }

    @Test
    fun `failed newer paging is blocked until a user scroll permits retry`() = runTest(dispatcher) {
        val delegate = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old", 1_000)))
        var fail = false
        val port = object : TrafficQuery by delegate {
            override suspend fun query(query: TrafficPageQuery): TrafficPage {
                if (fail && query.newerThan != null) error("newer-page-test-failure")
                return delegate.query(query)
            }
        }
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 1), true)
        delegate.record(snapshot("new", 2_000)); advanceUntilIdle()
        fail = true
        viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
        assertTrue(viewModel.uiState.value.newerPageBlocked)
        assertEquals(1L, viewModel.uiState.value.reading.newCount)
        viewModel.retainViewport(TrafficSource.All, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 2), true)
        assertEquals(false, viewModel.uiState.value.newerPageBlocked)
        fail = false
        viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
        assertEquals("new", viewModel.uiState.value.transactions.first().transactionId)
    }

    @Test
    fun `a paused paged source retains its older continuation when switching device tabs`() = runTest(dispatcher) {
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), (1..1_250).map { snapshot("row-$it", it * 1_000L) })
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port)
        advanceUntilIdle()
        val source = TrafficSource.Device(RegisteredDeviceId("paged-reader"))
        viewModel.selectSource(source); advanceUntilIdle()
        viewModel.retainViewport(source, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 1), true)
        viewModel.processIntent(TrafficIntent.LoadNextPage); advanceUntilIdle()
        assertEquals(400, viewModel.uiState.value.transactions.size)
        val cursor = assertNotNull(viewModel.uiState.value.nextPageCursor)
        viewModel.selectSource(TrafficSource.Other); advanceUntilIdle()
        viewModel.selectSource(source); advanceUntilIdle()
        assertEquals(cursor, viewModel.uiState.value.nextPageCursor)
        viewModel.processIntent(TrafficIntent.LoadNextPage); advanceUntilIdle()
        assertEquals(600, viewModel.uiState.value.transactions.size)
        assertEquals("row-651", viewModel.uiState.value.transactions.last().transactionId)
    }

    @Test
    fun `clearing a background device does not suspend the active device last seen progress`() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val maintenance = object : TrafficMaintenance {
            override suspend fun clearTerminalTraffic() = error("Expected scoped clear")
            override suspend fun clearSourceTraffic(source: TrafficSource) { entered.complete(Unit); release.await() }
        }
        val port = LiveTrafficPort(CaptureSessionId("fake-session"), listOf(snapshot("old", 1_000)))
        val viewModel = FakeTrafficViewModelFactory.create(customTrafficQueryPort = port, customTrafficMaintenance = maintenance)
        advanceUntilIdle()
        val active = TrafficSource.Device(RegisteredDeviceId("active-reader"))
        viewModel.selectSource(active); advanceUntilIdle()
        viewModel.retainViewport(active, com.devuloopers.knet.ui.desktop.traffic.model.TrafficViewport(0, 1), true)
        repeat(19) { port.record(snapshot("new-$it", 2_000L + it)) }; advanceUntilIdle()
        viewModel.processIntent(TrafficIntent.LoadNewerPage); advanceUntilIdle()
        viewModel.clearSource(TrafficSource.Device(RegisteredDeviceId("background-device")))
        runCurrent(); entered.await()
        assertTrue(viewModel.uiState.value.isClearingHistory)
        reportVisible(viewModel, listOf("new-0"))
        testScheduler.advanceTimeBy(100); runCurrent()
        assertEquals(18L, viewModel.uiState.value.reading.newCount)
        release.complete(Unit); advanceUntilIdle()
        assertEquals(18L, viewModel.uiState.value.reading.newCount)
        assertEquals(active, viewModel.uiState.value.source)
    }

    private fun reportVisible(viewModel: com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel,
        ids: List<String>, atTop: Boolean = false) {
        val state = viewModel.uiState.value
        viewModel.observeVisibleRows(state.source, state.readingEpoch,
            com.devuloopers.knet.ui.desktop.traffic.model.TrafficVisibleRows(ids, atTop))
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
        private val mutableGenerations = MutableSharedFlow<TrafficGeneration>(extraBufferCapacity = 2_048)
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
            fun point(snapshot: HttpExchangeSnapshot) = com.devuloopers.knet.application.contract.traffic.TrafficReadPoint(
                com.devuloopers.knet.application.contract.traffic.TrafficRecordId.Exchange(snapshot.id), snapshot.startedAtEpochMillis,
                sequenceById.getValue(snapshot.id))
            val anchor = query.newerThan ?: query.olderThan ?: query.cursor?.let { cursor ->
                snapshots.firstOrNull { it.id.value == cursor.value }?.let(::point)
            }
            val eligible = candidates.filter { snapshot ->
                (query.windowThrough == null || sequenceById.getValue(snapshot.id).value <= query.windowThrough!!.exchanges) &&
                    (anchor == null || if (query.direction == com.devuloopers.knet.application.contract.traffic.TrafficSortDirection.OLDEST_FIRST)
                        point(snapshot) > anchor else point(snapshot) < anchor)
            }.sortedWith(compareBy<HttpExchangeSnapshot> { point(it) }.let {
                if (query.direction == com.devuloopers.knet.application.contract.traffic.TrafficSortDirection.NEWEST_FIRST) it.reversed() else it
            })
            val selected = eligible.take(query.limit)
            return TrafficPage(
                arrivals = com.devuloopers.knet.application.contract.traffic.TrafficArrivals(
                    com.devuloopers.knet.application.contract.traffic.TrafficArrivalCheckpoint(snapshots.size.toLong(), 0),
                    query.arrivalsAfter?.let { checkpoint -> candidates.count { snapshot ->
                        val seen = query.seenThrough
                        point(snapshot).isAfter(checkpoint) && (seen == null ||
                            point(snapshot).captureSequence.value > seen.observedAt.exchanges || point(snapshot) > seen.position)
                    }.toLong() } ?: 0),
                latest = candidates.map(::point).maxOrNull(),
                items = selected.map { snapshot ->
                    val sequence = sequenceById.getValue(snapshot.id)
                    TrafficPageItem(captureSequence = sequence, historySequence = TrafficHistorySequence(sequence.value), exchange = snapshot)
                },
                nextCursor = selected.lastOrNull()?.takeIf { eligible.size > query.limit }?.let { TrafficPageCursor(it.id.value) },
                totalCount = candidates.size.toLong(),
                generation = generation,
            )
        }

        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? =
            snapshots.firstOrNull { it.id == exchangeId }

        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
            error("Live traffic rows must not read bodies.")

        fun invalidate() {
            generation++
            check(mutableGenerations.tryEmit(TrafficGeneration(sessionId, generation)))
        }

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
