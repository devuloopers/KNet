@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)


package com.devuloopers.knet.ui.desktop.traffic.viewmodel

import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsResult
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsUseCase
import com.devuloopers.knet.application.usecase.traffic.ClearTrafficHistoryUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveLatestTrafficSessionUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficGenerationsUseCase
import com.devuloopers.knet.application.usecase.traffic.PrepareTrafficRequestResult
import com.devuloopers.knet.application.usecase.traffic.PrepareTrafficRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.PreparedTrafficRequest
import com.devuloopers.knet.application.usecase.traffic.QueryTrafficPageUseCase
import com.devuloopers.knet.application.usecase.traffic.TrafficBodyPreview
import com.devuloopers.knet.application.usecase.traffic.PauseTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.ResumeTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingBreakpointsUseCase
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.port.inspection.ObserveInspectionAnnotationsUseCase
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.proxy.ProxyStopReason
import com.devuloopers.knet.application.port.traffic.CaptureSessionState
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.proxy.StartLoopbackProxyUseCase
import com.devuloopers.knet.application.usecase.proxy.StopProxyRuntimeUseCase
import com.devuloopers.knet.core.logger.KNetLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import com.devuloopers.knet.domain.traffic.model.*
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.util.decodeBodyToText
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase

import com.devuloopers.knet.ui.desktop.traffic.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.usecase.ObserveRulesUseCase
import kotlin.uuid.Uuid
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.http.HttpMethod as CanonicalHttpMethod
import com.devuloopers.knet.traffic.model.http.HttpStatus as CanonicalHttpStatus
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream

/**
 * ViewModel managing live traffic feed state, proxy engine lifecycle observation, filtering, and inspection selection.
 */
class TrafficViewModel(
    private val observeLatestTrafficSessionUseCase: ObserveLatestTrafficSessionUseCase,
    private val queryTrafficPageUseCase: QueryTrafficPageUseCase,
    private val observeTrafficGenerationsUseCase: ObserveTrafficGenerationsUseCase,
    private val clearTrafficHistoryUseCase: ClearTrafficHistoryUseCase,
    private val startLoopbackProxyUseCase: StartLoopbackProxyUseCase,
    private val stopProxyRuntimeUseCase: StopProxyRuntimeUseCase,
    observeProxyRuntimeStateUseCase: ObserveProxyRuntimeStateUseCase,
    private val pauseTrafficCaptureUseCase: PauseTrafficCaptureUseCase,
    private val resumeTrafficCaptureUseCase: ResumeTrafficCaptureUseCase,
    observeTrafficCaptureStateUseCase: ObserveTrafficCaptureStateUseCase,
    private val loadTrafficExchangeDetailsUseCase: LoadTrafficExchangeDetailsUseCase,
    observeLocalIpUseCase: ObserveLocalIpUseCase,
    private val getWorkspaceLayoutUseCase: GetWorkspaceLayoutUseCase,
    private val prepareTrafficRequestUseCase: PrepareTrafficRequestUseCase,
    private val observeInspectionAnnotationsUseCase: ObserveInspectionAnnotationsUseCase,
    observeRulesUseCase: ObserveRulesUseCase,
    observePendingBreakpointsUseCase: ObservePendingBreakpointsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<TrafficState> = _uiState.asStateFlow()
    private val proxyRuntimeStates = observeProxyRuntimeStateUseCase.execute()
    private val trafficCaptureStates = observeTrafficCaptureStateUseCase.execute()

    private val preparedStateCache = object : LinkedHashMap<String, InspectorPreparedState>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, InspectorPreparedState>?): Boolean {
            return size > 64
        }
    }

    private val captureControlIntent = MutableStateFlow(
        CaptureControlIntent(
            shouldCapture = trafficCaptureStates.value is CaptureSessionState.Capturing,
            revision = 0L,
        ),
    )
    private val pageLoadMutex = Mutex()
    private var filterRefreshJob: Job? = null
    private var durableTransactions: List<TrafficRowUiState> = emptyList()
    private var pendingBreakpoints: List<PendingBreakpoint> = emptyList()
    private val interceptionHistory = object :
        LinkedHashMap<String, TrafficInterceptionUiState.Matched>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, TrafficInterceptionUiState.Matched>?,
        ): Boolean = size > MAX_TRAFFIC_ROWS
    }

    /**
     * Asynchronously prepares a canonical captured request under a bounded whole-body budget and
     * maps it into the current API Studio contract.
     *
     * @param transactionId Unique UUID of the target transaction.
     * @param onSpecReady Callback executed on main thread with the constructed [NetworkRequestSpec].
     */
    fun exportToStudioSpec(
        transactionId: String,
        onSpecReady: (com.devuloopers.knet.domain.network.model.NetworkRequestSpec) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val spec = when (val result = prepareTrafficRequestUseCase.execute(ExchangeId(transactionId))) {
                is PrepareTrafficRequestResult.Found -> result.value.toNetworkRequestSpec()
                PrepareTrafficRequestResult.Missing,
                PrepareTrafficRequestResult.BodyUnavailable,
                is PrepareTrafficRequestResult.BodyTooLarge -> null
            }
            if (spec != null) {
                KNetLogger.info("TrafficViewModel") {
                    "[EXPORT TO STUDIO] Successfully built spec for transactionId=$transactionId method=${spec.method} url=${spec.url}"
                }
                withContext(Dispatchers.Main) {
                    onSpecReady(spec)
                }
            } else {
                KNetLogger.warn("TrafficViewModel") {
                    "[EXPORT TO STUDIO FAILED] Spec could not be resolved for transactionId=$transactionId"
                }
            }
        }
    }

    init {
        // Auto-clear traffic feed on startup if configured in settings
        viewModelScope.launch {
            val settings = getWorkspaceLayoutUseCase.execute().firstOrNull()
            if (settings?.autoClearTrafficOnStartup == true) {
                clearTrafficHistoryUseCase.execute()
            }
        }

        // Reactively observe active breakpoint rules from domain UseCase
        observeRulesUseCase()
            .onEach { rules ->
                _uiState.update { it.copy(activeBreakpointRules = rules) }
            }
            .launchIn(viewModelScope)

        // Pending breakpoint candidates are a bounded live projection, not a second traffic
        // repository. They decorate or temporarily supply the canonical exchange row by the same
        // ExchangeId while the network is suspended, and remain independent from body storage.
        observePendingBreakpointsUseCase.execute()
            .onEach { events ->
                pendingBreakpoints = events
                events.forEach(::rememberInterception)
                publishTrafficProjection()
            }
            .launchIn(viewModelScope)

        // Serialize desired capture state and dynamic port changes without cancelling lifecycle work.
        viewModelScope.launch {
            val proxyPorts = flow {
                var isFirst = true
                getWorkspaceLayoutUseCase.execute()
                    .map { settings -> settings.proxyPort }
                    .distinctUntilChanged()
                    .collect { port ->
                        if (!isFirst) delay(500.milliseconds)
                        isFirst = false
                        emit(port)
                    }
            }
            combine(captureControlIntent, proxyPorts) { intent, port ->
                CaptureCommand(
                    shouldCapture = intent.shouldCapture,
                    port = port,
                    revision = intent.revision,
                )
            }
                .distinctUntilChanged()
                .collect { command ->
                    if (command.shouldCapture) {
                        startOrResumeCapture(command.port)
                    } else {
                        pauseTrafficCaptureUseCase.execute()
                    }
                }
        }

        // Reactive Transaction Payload Preparation via flatMapLatest
        viewModelScope.launch {
            _uiState
                .map { it.selectedTransaction }
                .distinctUntilChanged()
                .flatMapLatest { tx ->
                    if (tx == null) {
                        return@flatMapLatest flowOf(InspectorPreparedState())
                    }
                    val preparation = flow {
                        val isPending = tx.status <= 0
                        val cached = synchronized(preparedStateCache) { preparedStateCache[tx.transactionId] }
                        if (cached != null && (isPending || cached.responseBodyText.isNotBlank())) {
                            emit(cached)
                            return@flow
                        }

                        emit(
                            InspectorPreparedState(
                                transactionId = tx.transactionId,
                                isPreparing = true
                            )
                        )

                        val prepared = kotlinx.coroutines.withContext(Dispatchers.Default) {
                            val detailResult = loadTrafficExchangeDetailsUseCase.execute(
                                ExchangeId(tx.transactionId),
                            )
                            val details = (detailResult as? LoadTrafficExchangeDetailsResult.Found)?.details
                            val requestBody = (details?.requestBody as? TrafficBodyPreview.Available)
                                ?.chunk
                            val responseBody = (details?.responseBody as? TrafficBodyPreview.Available)
                                ?.chunk
                            val requestHeaders = details?.exchange?.request?.head?.headers.orEmpty().map { header ->
                                header.name.value to header.value
                            }
                            val responseHeaders = details?.exchange?.response?.head?.headers.orEmpty().map { header ->
                                header.name.value to header.value
                            }

                            // Single pass: decode bytes + resolve BodyFormat off-thread together.
                            val requestBodySpec = PayloadInspectionSpec.fromBytes(
                                requestBody?.copyBytes(),
                                requestHeaders,
                            )
                            val responseBodySpec = PayloadInspectionSpec.fromBytes(
                                responseBody?.copyBytes(),
                                responseHeaders,
                            )

                            val state = InspectorPreparedState(
                                transactionId = tx.transactionId,
                                requestPayloadSpec = requestBodySpec,
                                responsePayloadSpec = responseBodySpec,
                                requestBodyTruncated = requestBody?.endOfBody == false,
                                responseBodyTruncated = responseBody?.endOfBody == false,
                                isPreparing = false
                            )

                            if (!isPending) {
                                synchronized(preparedStateCache) {
                                    preparedStateCache[tx.transactionId] = state
                                }
                            }

                            state
                        }

                        emit(prepared)
                    }
                    combine(
                        preparation,
                        observeInspectionAnnotationsUseCase.execute(ExchangeId(tx.transactionId))
                            .onStart { emit(emptyList()) },
                    ) { prepared, annotations ->
                        prepared.copy(annotations = annotations)
                    }
                }
                .collect { prepared ->
                    _uiState.update { it.copy(preparedState = prepared) }
                }
        }

        // 1. Observe Proxy Engine State
        viewModelScope.launch {
            proxyRuntimeStates.collect { runtimeState ->
                _uiState.update { current ->
                    val errorMessage = when (runtimeState) {
                        is ProxyRuntimeState.Failed -> runtimeState.code
                        is ProxyRuntimeState.Running,
                        ProxyRuntimeState.Starting -> null
                        else -> current.engineErrorMessage
                    }
                    current.copy(
                        engineState = runtimeState,
                        engineErrorMessage = errorMessage
                    )
                }
            }
        }

        // 2. Observe capture attachment independently from the persistent forwarding listener.
        viewModelScope.launch {
            trafficCaptureStates.collect { captureState ->
                _uiState.update { current ->
                    current.copy(
                        captureState = when (captureState) {
                            CaptureSessionState.Inactive -> CaptureState.STOPPED
                            CaptureSessionState.Starting -> CaptureState.STARTING
                            is CaptureSessionState.Capturing -> CaptureState.CAPTURING
                            CaptureSessionState.Paused -> CaptureState.PAUSED
                            is CaptureSessionState.Failed -> CaptureState.STOPPED
                        }
                    )
                }
            }
        }

        // 3. Observe Reactive Host Local IP Address
        viewModelScope.launch {
            observeLocalIpUseCase.execute().collect { ip ->
                _uiState.update { current ->
                    current.copy(localIpAddress = ip)
                }
            }
        }

        // 4. Observe only a compact session ID and store generations; rows are fetched as bounded keyset pages.
        viewModelScope.launch {
            observeLatestTrafficSessionUseCase.execute()
                .distinctUntilChanged()
                .collectLatest { sessionId ->
                    _uiState.update { current ->
                        current.copy(
                            sessionId = sessionId,
                            nextPageCursor = null,
                            pageGeneration = 0L,
                        )
                    }
                    if (sessionId == null) return@collectLatest
                    loadTrafficPage(reset = true)
                    observeTrafficGenerationsUseCase.execute()
                        .filter { generation -> generation.sessionId == sessionId }
                        .debounce(75.milliseconds)
                        .collect { generation ->
                            if (generation.generation > _uiState.value.pageGeneration) {
                                loadTrafficPage(reset = true)
                            }
                        }
                }
        }
    }

    fun processIntent(intent: TrafficIntent) {
        when (intent) {
            is TrafficIntent.StartCapture -> {
                captureControlIntent.update { current ->
                    CaptureControlIntent(shouldCapture = true, revision = current.revision + 1L)
                }
            }

            is TrafficIntent.StopCapture -> {
                captureControlIntent.update { current ->
                    CaptureControlIntent(shouldCapture = false, revision = current.revision + 1L)
                }
            }

            is TrafficIntent.ClearFeed -> {
                viewModelScope.launch {
                    clearTrafficHistoryUseCase.execute()
                    synchronized(preparedStateCache) {
                        preparedStateCache.clear()
                    }
                    durableTransactions = emptyList()
                    interceptionHistory.clear()
                    _uiState.update {
                        it.copy(
                            transactions = emptyList(),
                            filteredTransactions = emptyList(),
                            selectedTransactionId = null,
                            preparedState = InspectorPreparedState(),
                        )
                    }
                    publishTrafficProjection()
                }
            }

            is TrafficIntent.ToggleAutoScroll -> {
                _uiState.update { it.copy(autoScroll = !it.autoScroll) }
            }

            is TrafficIntent.DismissEngineError -> {
                _uiState.update { it.copy(engineErrorMessage = null) }
            }

            is TrafficIntent.LoadNextPage -> {
                viewModelScope.launch { loadTrafficPage(reset = false) }
            }

            is TrafficIntent.Search -> {
                _uiState.update { current -> current.copy(searchQuery = intent.query) }
                publishTrafficProjection()
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByProtocol -> {
                _uiState.update { current -> current.copy(selectedProtocolFilter = intent.protocol) }
                publishTrafficProjection()
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByMethod -> {
                _uiState.update { current -> current.copy(selectedMethodFilter = intent.method) }
                publishTrafficProjection()
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByStatus -> {
                _uiState.update { current -> current.copy(selectedStatusFilter = intent.status) }
                publishTrafficProjection()
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.SelectTransaction -> {
                _uiState.update { it.copy(selectedTransactionId = intent.id) }
            }

            is TrafficIntent.SelectInspectorTab -> {
                _uiState.update { it.copy(activeInspectorTab = intent.tab) }
            }

            is TrafficIntent.SelectRequestSubTab -> {
                _uiState.update { it.copy(activeRequestSubTab = intent.subTab) }
            }

            is TrafficIntent.SelectResponseSubTab -> {
                _uiState.update { it.copy(activeResponseSubTab = intent.subTab) }
            }

            is TrafficIntent.SetPreviewFormatMode -> {
                _uiState.update { it.copy(previewFormatMode = intent.mode) }
            }

            is TrafficIntent.ToggleColumn -> {
                _uiState.update { current ->
                    current.copy(columnVisibility = current.columnVisibility.toggle(intent.column))
                }
            }
        }
    }

    /** Reconciles capture intent while preserving the listener unless its configured port changed. */
    private suspend fun startOrResumeCapture(port: Int) {
        val runtimeState = proxyRuntimeStates.value
        if (runtimeState is ProxyRuntimeState.Running) {
            val activePort = runtimeState.handle.endpoints.endpoints.singleOrNull()?.port
            if (activePort == port) {
                resumeTrafficCaptureUseCase.execute()
                return
            }
            stopProxyRuntimeUseCase.execute(ProxyStopReason.CONFIGURATION_CHANGED)
        }
        startLoopbackProxyUseCase.execute(port)
    }

    private fun scheduleFilteredPageRefresh() {
        filterRefreshJob?.cancel()
        filterRefreshJob = viewModelScope.launch {
            delay(150.milliseconds)
            loadTrafficPage(reset = true)
        }
    }

    /** Retains a bounded process-session marker after an active pause has been resolved. */
    private fun rememberInterception(event: PendingBreakpoint) {
        val exchangeId = event.candidate.exchangeId.value
        val previous = interceptionHistory[exchangeId]
        interceptionHistory[exchangeId] = TrafficInterceptionUiState.Matched(
            ruleIds = previous?.ruleIds.orEmpty() + event.ruleId,
            phases = previous?.phases.orEmpty() + event.candidate.phase,
        )
    }

    /**
     * Joins durable page metadata with the bounded pending-breakpoint projection by ExchangeId.
     *
     * Pending rows win only while suspended, are always visible despite ordinary traffic filters,
     * and never own body bytes. Once the canonical row arrives it replaces the provisional row
     * without changing identity, ordering, or sequence numbering.
     */
    private fun publishTrafficProjection() {
        val pendingByExchange = pendingBreakpoints.associateBy { event -> event.candidate.exchangeId.value }
        val durableByExchange = durableTransactions.associateBy(TrafficRowUiState::transactionId)
        val projectedDurable = durableTransactions.map { row ->
            val historical = interceptionHistory[row.transactionId]
            if (historical == null) row else row.copy(interception = historical)
        }
        val pendingRows = pendingByExchange.map { (exchangeId, event) ->
            val paused = TrafficInterceptionUiState.Paused(
                pendingId = event.id,
                ruleId = event.ruleId,
                phase = event.candidate.phase,
            )
            durableByExchange[exchangeId]?.copy(
                status = 0,
                statusText = "In Progress",
                formattedTime = "-",
                interception = paused,
            ) ?: event.toTrafficRowUiState()
        }
        val pendingIds = pendingByExchange.keys
        val sortedRows = (pendingRows + projectedDurable.filterNot { row -> row.transactionId in pendingIds })
            .sortedWith(
                compareByDescending<TrafficRowUiState> { row -> row.timestamp }
                    .thenByDescending(TrafficRowUiState::transactionId),
            )
            .take(MAX_TRAFFIC_ROWS)
        val rows = sortedRows.mapIndexed { index, row ->
            row.copy(sequenceNumber = sortedRows.size - index)
        }
        val current = _uiState.value
        val ordinarilyFiltered = applyFilters(
            transactions = rows,
            query = current.searchQuery,
            protocol = current.selectedProtocolFilter,
            method = current.selectedMethodFilter,
            status = current.selectedStatusFilter,
        )
        val activeRows = rows.filter { row -> row.interception is TrafficInterceptionUiState.Paused }
        val visibleRows = (activeRows + ordinarilyFiltered)
            .distinctBy(TrafficRowUiState::transactionId)
            .sortedWith(
                compareByDescending<TrafficRowUiState> { row -> row.timestamp }
                    .thenByDescending(TrafficRowUiState::transactionId),
            )
        val selectedId = current.selectedTransactionId
            ?.takeIf { id -> rows.any { row -> row.transactionId == id } }
            ?: rows.firstOrNull()?.transactionId
        _uiState.update { state ->
            state.copy(
                transactions = rows,
                filteredTransactions = visibleRows,
                selectedTransactionId = selectedId,
            )
        }
    }

    /** Loads one keyset page and never retains more than [MAX_TRAFFIC_ROWS] body-free rows. */
    private suspend fun loadTrafficPage(reset: Boolean) {
        pageLoadMutex.withLock {
            val before = _uiState.value
            val sessionId = before.sessionId ?: return
            val cursor = if (reset) null else before.nextPageCursor ?: return
            _uiState.update { it.copy(isPageLoading = true) }
            try {
                val page = queryTrafficPageUseCase.execute(
                    TrafficPageQuery(
                        sessionId = sessionId,
                        cursor = cursor,
                        limit = TRAFFIC_PAGE_SIZE,
                        hostContains = before.searchQuery.trim().takeIf { it.isNotBlank() },
                        methods = before.selectedMethodFilter.toCanonicalMethods(),
                        statuses = before.selectedStatusFilter.toCanonicalStatuses(),
                    ),
                )
                val latestState = _uiState.value
                if (latestState.sessionId != sessionId) return
                val refreshedRows = page.items.map { snapshot -> snapshot.toTrafficRowUiState() }
                val refreshedIds = refreshedRows.asSequence().map { it.transactionId }.toHashSet()
                val sortedRows = (refreshedRows + durableTransactions.filterNot {
                    it.transactionId in refreshedIds
                })
                    .sortedWith(
                        compareByDescending<TrafficRowUiState> { it.timestamp }
                            .thenByDescending { it.transactionId },
                    )
                    .take(MAX_TRAFFIC_ROWS)
                val filtersStillMatch = latestState.searchQuery == before.searchQuery &&
                    latestState.selectedProtocolFilter == before.selectedProtocolFilter &&
                    latestState.selectedMethodFilter == before.selectedMethodFilter &&
                    latestState.selectedStatusFilter == before.selectedStatusFilter
                if (!filtersStillMatch) return
                durableTransactions = sortedRows
                _uiState.update { current ->
                    current.copy(
                        nextPageCursor = page.nextCursor.takeIf { sortedRows.size < MAX_TRAFFIC_ROWS },
                        pageGeneration = maxOf(current.pageGeneration, page.generation),
                    )
                }
                publishTrafficProjection()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                KNetLogger.error("TrafficViewModel", error) {
                    "Traffic page query failed for session=${sessionId.value} reset=$reset"
                }
            } finally {
                _uiState.update { it.copy(isPageLoading = false) }
            }
        }
    }

    private fun applyFilters(
        transactions: List<TrafficRowUiState>,
        query: String,
        protocol: ProtocolFilter,
        method: MethodFilter,
        status: StatusFilter
    ): List<TrafficRowUiState> {
        return transactions.filter { item ->
            val matchesQuery = query.isBlank() ||
                    item.host.contains(query, ignoreCase = true) ||
                    item.path.contains(query, ignoreCase = true) ||
                    item.method.contains(query, ignoreCase = true) ||
                    item.status.toString().contains(query)

            val matchesProtocol = when (protocol) {
                ProtocolFilter.ALL -> true
                ProtocolFilter.HTTP -> item.protocol.startsWith("HTTP/1")
                ProtocolFilter.HTTPS -> item.protocol.startsWith("HTTP/2") || item.protocol.equals(
                    "HTTPS",
                    ignoreCase = true
                )

                ProtocolFilter.WEBSOCKET -> item.method.equals("WS", ignoreCase = true) || item.protocol.equals(
                    "WS",
                    ignoreCase = true
                )

                ProtocolFilter.HTTP_2 -> item.protocol.contains("2")
                ProtocolFilter.GRPC -> item.path.contains("grpc", ignoreCase = true)
                ProtocolFilter.OTHER -> !item.protocol.startsWith("HTTP/1") && !item.protocol.startsWith("HTTP/2") && !item.protocol.equals(
                    "HTTPS",
                    ignoreCase = true
                ) && !item.method.equals("WS", ignoreCase = true) && !item.protocol.equals("WS", ignoreCase = true)
            }

            val matchesMethod = when (method) {
                MethodFilter.ALL -> true
                else -> item.method.equals(method.name, ignoreCase = true)
            }

            val matchesStatus = when (status) {
                StatusFilter.ALL -> true
                else -> status.range?.contains(item.status) ?: true
            }

            matchesQuery && matchesProtocol && matchesMethod && matchesStatus
        }
    }

    /**
     * Constructs a pre-populated [BreakpointRule] from a captured transaction and opens the Add/Edit dialog.
     *
     * @param transactionId Target transaction UUID.
     */
    fun createBreakpointFromTransaction(transactionId: String) {
        val item = uiState.value.transactions.find { it.transactionId == transactionId } ?: return
        val targetUrl = item.fullUrl

        val prefilledModel = BreakpointRule(
            id = Uuid.random().toString(),
            name = targetUrl,
            phase = com.devuloopers.knet.domain.rules.model.BreakpointPhase.BOTH,
            urlPattern = targetUrl,
            method = CanonicalHttpMethod.fromToken(item.method),
            enabled = true,
            protocolCriteria = com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.HttpDefault,
        )

        _uiState.update { state ->
            state.copy(
                isBreakpointDialogVisible = true,
                prefilledBreakpointRule = prefilledModel
            )
        }
    }

    /**
     * Closes the traffic workspace breakpoint rule dialog.
     */
    fun closeBreakpointDialog() {
        _uiState.update { it.copy(isBreakpointDialogVisible = false, prefilledBreakpointRule = null) }
    }

    private fun createInitialState(): TrafficState {
        return TrafficState(
            transactions = emptyList(),
            filteredTransactions = emptyList(),
            selectedTransactionId = null,
            captureState = CaptureState.STOPPED,
            engineState = ProxyRuntimeState.Stopped,
            searchQuery = "",
            selectedProtocolFilter = ProtocolFilter.ALL,
            selectedMethodFilter = MethodFilter.ALL,
            selectedStatusFilter = StatusFilter.ALL,
            autoScroll = true,
            activeInspectorTab = InspectorTab.OVERVIEW,
            previewFormatMode = PreviewFormatMode.PRETTY
        )
    }

    private companion object {
        private const val TRAFFIC_PAGE_SIZE = 200
        private const val MAX_TRAFFIC_ROWS = 1_000
    }

    /** Conflatable desired capture command derived from settings and toolbar state. */
    private data class CaptureCommand(
        val shouldCapture: Boolean,
        val port: Int,
        val revision: Long,
    )

    /** User capture intent with a revision so retries remain observable without transient states. */
    private data class CaptureControlIntent(
        val shouldCapture: Boolean,
        val revision: Long,
    )
}

private fun MethodFilter.toCanonicalMethods(): Set<CanonicalHttpMethod> = when (this) {
    MethodFilter.ALL -> emptySet()
    else -> setOf(CanonicalHttpMethod.fromToken(name))
}

private fun StatusFilter.toCanonicalStatuses(): Set<CanonicalHttpStatus> =
    range?.map(::CanonicalHttpStatus)?.toSet().orEmpty()

private fun PreparedTrafficRequest.toNetworkRequestSpec(): NetworkRequestSpec {
    val headers = request.head.headers.map { header -> header.name.value to header.value }
    val url = request.head.target.toAbsoluteUrl(headers)
    val bodyBytes = ByteArrayOutputStream(bodyChunks.sumOf { it.size }).use { output ->
        bodyChunks.forEach { chunk -> output.write(chunk.copyBytes()) }
        output.toByteArray()
    }
    return NetworkRequestSpec(
        method = request.head.method,
        url = url,
        headers = headers,
        queryParams = parseQueryPairs(url),
        bodyPayload = decodeBodyToText(bodyBytes.takeIf { it.isNotEmpty() }, headers),
        timestamp = startedAtEpochMillis,
    )
}

private fun RequestTarget.toAbsoluteUrl(headers: List<Pair<String, String>>): String = when (this) {
    is RequestTarget.Absolute -> {
        val portText = authority.port?.let { ":$it" }.orEmpty()
        "${scheme.token}://${authority.host}$portText$pathAndQuery"
    }
    is RequestTarget.Origin -> {
        val host = headers.firstOrNull { it.first.equals("Host", ignoreCase = true) }?.second.orEmpty()
        val scheme = if (host.substringAfterLast(':', "") == "443") "https" else "http"
        "$scheme://$host$pathAndQuery"
    }
    is RequestTarget.AuthorityForm -> {
        val portText = authority.port?.let { ":$it" }.orEmpty()
        "https://${authority.host}$portText/"
    }
    RequestTarget.Asterisk -> "*"
    is RequestTarget.Custom -> value
}

private fun parseQueryPairs(url: String): List<Pair<String, String>> {
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isBlank()) return emptyList()
    return query.split('&').mapNotNull { pair ->
        val name = pair.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        name to pair.substringAfter('=', "")
    }
}
