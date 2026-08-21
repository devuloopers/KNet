@file:OptIn(ExperimentalCoroutinesApi::class)

package com.devuloopers.knet.ui.desktop.traffic.viewmodel

import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsResult
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsUseCase
import com.devuloopers.knet.application.usecase.traffic.ClearTrafficHistoryUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveLatestTrafficSessionUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficGenerationsUseCase
import com.devuloopers.knet.application.usecase.traffic.PrepareCapturedNetworkRequestResult
import com.devuloopers.knet.application.usecase.traffic.PrepareCapturedNetworkRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.QueryTrafficPageUseCase
import com.devuloopers.knet.application.usecase.traffic.TrafficBodyPreview
import com.devuloopers.knet.application.usecase.traffic.PauseTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.ResumeTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.PrepareBreakpointRuleDraftResult
import com.devuloopers.knet.application.usecase.breakpoint.PrepareBreakpointRuleDraftUseCase
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
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptor
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorBody
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorInput
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import com.devuloopers.knet.domain.workspace.model.TrafficTableColumnWidths
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.UpdateWorkspaceLayoutUseCase

import com.devuloopers.knet.ui.desktop.traffic.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds
import com.devuloopers.knet.domain.rules.usecase.ObserveRulesUseCase
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.http.HttpMethod as CanonicalHttpMethod
import com.devuloopers.knet.traffic.model.http.HttpStatus as CanonicalHttpStatus
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.StandardHttpScheme
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val observeApplicationSettingsUseCase: ObserveApplicationSettingsUseCase,
    private val prepareCapturedNetworkRequestUseCase: PrepareCapturedNetworkRequestUseCase,
    private val observeInspectionAnnotationsUseCase: ObserveInspectionAnnotationsUseCase,
    private val describeRequestUseCase: DescribeRequestUseCase,
    private val prepareBreakpointRuleDraftUseCase: PrepareBreakpointRuleDraftUseCase,
    observeRulesUseCase: ObserveRulesUseCase,
    observePendingBreakpointsUseCase: ObservePendingBreakpointsUseCase,
    getWorkspaceLayoutUseCase: GetWorkspaceLayoutUseCase,
    private val updateWorkspaceLayoutUseCase: UpdateWorkspaceLayoutUseCase,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<TrafficState> = _uiState.asStateFlow()
    private val proxyRuntimeStates = observeProxyRuntimeStateUseCase.execute()
    private val trafficCaptureStates = observeTrafficCaptureStateUseCase.execute()

    private val preparedStateCache = InspectorPreparedCache(
        maximumEntries = MAX_PREPARED_INSPECTOR_ENTRIES,
        maximumRetainedBytes = MAX_PREPARED_INSPECTOR_BYTES,
    )

    private val captureControlIntent = MutableStateFlow(
        CaptureControlIntent(
            shouldCapture = trafficCaptureStates.value is CaptureSessionState.Capturing,
            revision = 0L,
        ),
    )
    private val pageLoadMutex = Mutex()
    private val clearPresentationMutex = Mutex()
    private var filterRefreshJob: Job? = null
    private var breakpointDraftJob: Job? = null
    private var pendingDescriptorJob: Job? = null
    private var durableAnnotationJob: Job? = null
    private var durableTransactions: List<TrafficRowUiState> = emptyList()
    private var pendingBreakpoints: List<PendingBreakpoint> = emptyList()
    private var pendingRequestDescriptors: Map<String, RequestDescriptor> = emptyMap()
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
     * @param onSpecReady Callback executed on main thread with the constructed request specification.
     */
    fun exportToStudioSpec(
        transactionId: String,
        onSpecReady: (com.devuloopers.knet.domain.network.model.NetworkRequestSpec) -> Unit
    ) {
        viewModelScope.launch(backgroundDispatcher) {
            val spec = when (val result = prepareCapturedNetworkRequestUseCase.execute(ExchangeId(transactionId))) {
                is PrepareCapturedNetworkRequestResult.Found -> result.spec
                PrepareCapturedNetworkRequestResult.Missing,
                PrepareCapturedNetworkRequestResult.BodyUnavailable,
                PrepareCapturedNetworkRequestResult.IncompleteTarget,
                is PrepareCapturedNetworkRequestResult.BodyTooLarge -> null
            }
            if (spec != null) {
                KNetLogger.info("TrafficViewModel") {
                    "[EXPORT TO STUDIO] Successfully built spec for transactionId=$transactionId method=${spec.method}"
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
        getWorkspaceLayoutUseCase.execute()
            .map { settings -> settings.trafficTableColumnWidths }
            .distinctUntilChanged()
            .onEach { widths ->
                _uiState.update { current -> current.copy(columnWidths = widths) }
            }
            .launchIn(viewModelScope)

        // Reactively observe active breakpoint rules from domain UseCase
        observeRulesUseCase()
            .onEach { rules ->
                _uiState.update { it.copy(activeBreakpointRules = rules) }
                schedulePendingRequestDescriptors()
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
                schedulePendingRequestDescriptors()
            }
            .launchIn(viewModelScope)

        // Serialize desired capture state and dynamic port changes without cancelling lifecycle work.
        viewModelScope.launch {
            val proxyPorts = observeApplicationSettingsUseCase.execute()
                .map { settings -> settings.proxyPort.value }
                .distinctUntilChanged()
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
                        val cached = preparedStateCache[tx.transactionId]
                        if (cached != null) {
                            emit(cached)
                            return@flow
                        }

                        emit(InspectorPreparedState.loading(tx.transactionId))

                        val pendingExchange = pendingBreakpoints
                            .firstOrNull { event -> event.candidate.exchangeId.value == tx.transactionId }
                            ?.toTrafficExchangeSnapshot()

                        val prepared = withContext(backgroundDispatcher) {
                            val detailResult = loadTrafficExchangeDetailsUseCase.execute(
                                ExchangeId(tx.transactionId),
                            )
                            val details = (detailResult as? LoadTrafficExchangeDetailsResult.Found)?.details
                            val exchange = details?.exchange ?: pendingExchange
                            if (exchange == null) {
                                return@withContext InspectorPreparedState(
                                    transactionId = tx.transactionId,
                                    loadState = InspectorLoadState.MISSING,
                                )
                            }
                            val requestBody = (details?.requestBody as? TrafficBodyPreview.Available)
                                ?.chunk
                            val responseBody = (details?.responseBody as? TrafficBodyPreview.Available)
                                ?.chunk
                            val requestHeaders = exchange.request.head.headers.map { header ->
                                header.name.value to header.value
                            }
                            val responseHeaders = exchange.response?.head?.headers.orEmpty().map { header ->
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

                            InspectorPreparedState(
                                transactionId = tx.transactionId,
                                exchange = exchange,
                                requestPayloadSpec = requestBodySpec,
                                responsePayloadSpec = responseBodySpec,
                                requestBodyTruncated = requestBody?.endOfBody == false,
                                responseBodyTruncated = responseBody?.endOfBody == false,
                                loadState = InspectorLoadState.READY,
                            )
                        }

                        if (pendingExchange == null && tx.status > 0 && prepared.loadState == InspectorLoadState.READY) {
                            preparedStateCache.put(prepared)
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
                        },
                        trafficErrorMessage = when (captureState) {
                            is CaptureSessionState.Failed -> "Capture unavailable (${captureState.code})"
                            CaptureSessionState.Starting,
                            is CaptureSessionState.Capturing,
                            CaptureSessionState.Paused -> null
                            CaptureSessionState.Inactive -> current.trafficErrorMessage
                        },
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
                            totalAvailableCount = 0L,
                            pageGeneration = 0L,
                        )
                    }
                    if (sessionId == null) return@collectLatest
                    loadTrafficPage(TrafficPageLoadMode.REPLACE)
                    observeTrafficGenerationsUseCase.execute()
                        .filter { generation -> generation.sessionId == sessionId }
                        .conflate()
                        .onEach { delay(75.milliseconds) }
                        .collect { generation ->
                            if (generation.generation > _uiState.value.pageGeneration) {
                                loadTrafficPage(TrafficPageLoadMode.REFRESH_NEWEST)
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

            is TrafficIntent.PauseCapture -> {
                captureControlIntent.update { current ->
                    CaptureControlIntent(shouldCapture = false, revision = current.revision + 1L)
                }
            }

            is TrafficIntent.ClearFeed -> {
                viewModelScope.launch {
                    clearPresentationMutex.withLock {
                        if (_uiState.value.isClearingHistory) return@withLock
                        _uiState.update { state -> state.copy(isClearingHistory = true, trafficErrorMessage = null) }
                        try {
                            clearTrafficHistoryUseCase.execute()
                            pageLoadMutex.withLock {
                                durableAnnotationJob?.cancel()
                                preparedStateCache.clear()
                                durableTransactions = emptyList()
                                interceptionHistory.clear()
                                _uiState.update { state ->
                                    state.copy(
                                        transactions = emptyList(),
                                        filteredTransactions = emptyList(),
                                        totalAvailableCount = 0L,
                                        selectedTransactionId = null,
                                        preparedState = InspectorPreparedState(),
                                        nextPageCursor = null,
                                        pageGeneration = 0L,
                                    )
                                }
                            }
                            publishTrafficProjection()
                            loadTrafficPage(TrafficPageLoadMode.REPLACE)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            KNetLogger.error("TrafficViewModel", error) { "Traffic history clear failed." }
                            _uiState.update { state ->
                                state.copy(trafficErrorMessage = "Traffic history could not be cleared.")
                            }
                        } finally {
                            _uiState.update { state -> state.copy(isClearingHistory = false) }
                        }
                    }
                }
            }

            is TrafficIntent.ToggleAutoScroll -> {
                _uiState.update { it.copy(autoScroll = !it.autoScroll) }
            }

            is TrafficIntent.DismissEngineError -> {
                _uiState.update { it.copy(engineErrorMessage = null, trafficErrorMessage = null) }
            }

            is TrafficIntent.LoadNextPage -> {
                viewModelScope.launch { loadTrafficPage(TrafficPageLoadMode.LOAD_OLDER) }
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
                _uiState.update { current ->
                    val preparedState = intent.id
                        ?.let { id -> current.preparedState.forSelection(id) ?: InspectorPreparedState.loading(id) }
                        ?: InspectorPreparedState()
                    current.copy(
                        selectedTransactionId = intent.id,
                        preparedState = preparedState,
                    )
                }
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

            is TrafficIntent.ToggleColumn -> {
                _uiState.update { current ->
                    current.copy(columnVisibility = current.columnVisibility.toggle(intent.column))
                }
            }

            is TrafficIntent.ResizeColumn -> {
                _uiState.update { current ->
                    current.copy(
                        columnWidths = current.columnWidths.withColumnWidth(intent.column, intent.widthDp),
                    )
                }
            }

            TrafficIntent.CommitColumnWidths -> persistColumnWidths(_uiState.value.columnWidths)

            is TrafficIntent.ResetColumnWidth -> {
                val widths = _uiState.value.columnWidths.resetColumn(intent.column)
                _uiState.update { current -> current.copy(columnWidths = widths) }
                persistColumnWidths(widths)
            }

            TrafficIntent.ResetColumnWidths -> {
                val widths = TrafficTableColumnWidths()
                _uiState.update { current -> current.copy(columnWidths = widths) }
                persistColumnWidths(widths)
            }
        }
    }

    private fun persistColumnWidths(widths: TrafficTableColumnWidths) {
        viewModelScope.launch {
            updateWorkspaceLayoutUseCase.execute { settings ->
                settings.copy(trafficTableColumnWidths = widths)
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
            loadTrafficPage(TrafficPageLoadMode.REPLACE)
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
        val maximumDurableSequence = durableTransactions.maxOfOrNull(TrafficRowUiState::sequenceNumber) ?: 0L
        val provisionalSequences = pendingBreakpoints
            .filterNot { event -> event.candidate.exchangeId.value in durableByExchange }
            .sortedWith(
                compareBy<PendingBreakpoint> { event -> event.candidate.startedAtEpochMillis }
                    .thenBy { event -> event.candidate.exchangeId.value },
            )
            .mapIndexed { index, event ->
                event.candidate.exchangeId.value to maximumDurableSequence + index + 1L
            }
            .toMap()
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
            )?.let { row ->
                pendingRequestDescriptors[event.id]?.let(row::withDescriptor) ?: row
            } ?: event.toTrafficRowUiState(pendingRequestDescriptors[event.id]).copy(
                sequenceNumber = provisionalSequences.getValue(exchangeId),
            )
        }
        val pendingIds = pendingByExchange.keys
        val sortedRows = (pendingRows + projectedDurable.filterNot { row -> row.transactionId in pendingIds })
            .sortedWith(
                compareByDescending<TrafficRowUiState> { row -> row.sequenceNumber }
                    .thenByDescending { row -> row.timestamp }
                    .thenByDescending(TrafficRowUiState::transactionId),
            )
            .take(MAX_TRAFFIC_ROWS)
        val rows = sortedRows
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
                compareByDescending<TrafficRowUiState> { row -> row.sequenceNumber }
                    .thenByDescending { row -> row.timestamp }
                    .thenByDescending(TrafficRowUiState::transactionId),
            )
        val selectedId = current.selectedTransactionId
            ?.takeIf { id -> visibleRows.any { row -> row.transactionId == id } }
            ?: visibleRows.firstOrNull()?.transactionId
        _uiState.update { state ->
            val preparedState = selectedId
                ?.let { id -> state.preparedState.forSelection(id) ?: InspectorPreparedState.loading(id) }
                ?: InspectorPreparedState()
            state.copy(
                transactions = rows,
                filteredTransactions = visibleRows,
                selectedTransactionId = selectedId,
                preparedState = preparedState,
            )
        }
    }

    /** Loads one keyset page and never retains more than [MAX_TRAFFIC_ROWS] body-free rows. */
    private suspend fun loadTrafficPage(mode: TrafficPageLoadMode) {
        pageLoadMutex.withLock {
            val before = _uiState.value
            val sessionId = before.sessionId ?: return
            val cursor = when (mode) {
                TrafficPageLoadMode.REPLACE,
                TrafficPageLoadMode.REFRESH_NEWEST -> null
                TrafficPageLoadMode.LOAD_OLDER -> before.nextPageCursor ?: return
            }
            _uiState.update { it.copy(isPageLoading = true) }
            try {
                val page = queryTrafficPageUseCase.execute(
                    TrafficPageQuery(
                        sessionId = null,
                        cursor = cursor,
                        limit = TRAFFIC_PAGE_SIZE,
                        searchContains = before.searchQuery.trim().takeIf { it.isNotBlank() },
                        methods = before.selectedMethodFilter.toCanonicalMethods(),
                        statuses = before.selectedStatusFilter.toCanonicalStatuses(),
                        schemes = before.selectedProtocolFilter.toCanonicalSchemes(),
                        protocols = before.selectedProtocolFilter.toCanonicalProtocols(),
                    ),
                )
                val latestState = _uiState.value
                if (latestState.sessionId != sessionId) return
                val refreshedRows = page.items.map { item ->
                    item.exchange
                        .toTrafficRowUiState(item.captureSequence.value)
                        .withDescriptor(describeRequestUseCase.execute(item.exchange.request))
                }
                val sortedRows = mergePageRows(mode, refreshedRows)
                val filtersStillMatch = latestState.searchQuery == before.searchQuery &&
                    latestState.selectedProtocolFilter == before.selectedProtocolFilter &&
                    latestState.selectedMethodFilter == before.selectedMethodFilter &&
                    latestState.selectedStatusFilter == before.selectedStatusFilter
                if (!filtersStillMatch) return
                durableTransactions = sortedRows
                observeDurableRequestKinds()
                _uiState.update { current ->
                    current.copy(
                        nextPageCursor = when (mode) {
                            TrafficPageLoadMode.REFRESH_NEWEST -> current.nextPageCursor ?: page.nextCursor
                            TrafficPageLoadMode.REPLACE,
                            TrafficPageLoadMode.LOAD_OLDER -> page.nextCursor
                        },
                        totalAvailableCount = when (mode) {
                            TrafficPageLoadMode.REPLACE,
                            TrafficPageLoadMode.REFRESH_NEWEST -> page.totalCount
                            TrafficPageLoadMode.LOAD_OLDER -> current.totalAvailableCount
                        },
                        pageGeneration = maxOf(current.pageGeneration, page.generation),
                    )
                }
                publishTrafficProjection()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                KNetLogger.error("TrafficViewModel", error) {
                    "Traffic page query failed for session=${sessionId.value} mode=$mode"
                }
            } finally {
                _uiState.update { it.copy(isPageLoading = false) }
            }
        }
    }

    /** Merges a page according to its navigation purpose while retaining a bounded rolling window. */
    private fun mergePageRows(
        mode: TrafficPageLoadMode,
        pageRows: List<TrafficRowUiState>,
    ): List<TrafficRowUiState> {
        if (mode == TrafficPageLoadMode.REPLACE) return pageRows.take(MAX_TRAFFIC_ROWS)
        val pageIds = pageRows.asSequence().map(TrafficRowUiState::transactionId).toHashSet()
        val sorted = (pageRows + durableTransactions.filterNot { row -> row.transactionId in pageIds })
            .sortedWith(
                compareByDescending<TrafficRowUiState> { row -> row.sequenceNumber }
                    .thenByDescending { row -> row.timestamp }
                    .thenByDescending(TrafficRowUiState::transactionId),
            )
        return when (mode) {
            TrafficPageLoadMode.REPLACE -> error("Replace pages are handled before merging.")
            TrafficPageLoadMode.REFRESH_NEWEST -> sorted.take(MAX_TRAFFIC_ROWS)
            TrafficPageLoadMode.LOAD_OLDER -> sorted.takeLast(MAX_TRAFFIC_ROWS)
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
                    item.displayMethod.contains(query, ignoreCase = true) ||
                    item.status.toString().contains(query)

            val matchesProtocol = protocol.matches(item)

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
     * Resolves semantic identities for pending rows away from Compose and retains no body bytes.
     *
     * The queue is published first with its HTTP fallback. Descriptor completion then decorates the
     * same exchange rows without delaying the interception drawer.
     */
    private fun schedulePendingRequestDescriptors() {
        pendingDescriptorJob?.cancel()
        val events = pendingBreakpoints
        if (events.isEmpty()) {
            pendingRequestDescriptors = emptyMap()
            return
        }
        val rulesById = _uiState.value.activeBreakpointRules.associateBy { it.id }
        pendingDescriptorJob = viewModelScope.launch {
            val descriptors = withContext(backgroundDispatcher) {
                events.associate { event ->
                    val requestBody = event.candidate.requestBody
                    val retainedBody = requestBody?.let { body ->
                        RequestDescriptorBody(body.copyBytes(RequestDescriptorBody.MAXIMUM_BYTES))
                    }
                    val bodyComplete = when {
                        requestBody == null -> event.candidate.requestObservedBodyBytes == 0L
                        requestBody.size > RequestDescriptorBody.MAXIMUM_BYTES -> false
                        else -> event.candidate.requestObservedBodyBytes == requestBody.size.toLong()
                    }
                    val kindHint = rulesById[event.ruleId]
                        ?.protocolCriteria
                        ?.protocolId
                        ?.value
                        ?.toRequestKindIdOrNull()
                    event.id to describeRequestUseCase.execute(
                        request = event.candidate.request,
                        body = retainedBody,
                        bodyComplete = bodyComplete,
                        semanticKindHint = kindHint,
                    )
                }
            }
            if (pendingBreakpoints.map(PendingBreakpoint::id) == events.map(PendingBreakpoint::id)) {
                pendingRequestDescriptors = descriptors
                publishTrafficProjection()
            }
        }
    }

    /**
     * Observes persisted semantic kinds for the bounded retained Traffic window as one Room flow.
     *
     * Semantic inspectors parse bodies once after capture. This projection consumes only their small
     * annotations and therefore keeps row rendering body-free even for large traffic histories.
     */
    private fun observeDurableRequestKinds() {
        durableAnnotationJob?.cancel()
        val exchangeIds = durableTransactions.asSequence()
            .take(MAXIMUM_ANNOTATION_OBSERVATION_ROWS)
            .map { ExchangeId(it.transactionId) }
            .toSet()
        if (exchangeIds.isEmpty()) return

        durableAnnotationJob = viewModelScope.launch {
            observeInspectionAnnotationsUseCase.execute(exchangeIds).collect { annotationsByExchange ->
                durableTransactions = durableTransactions.map { row ->
                    val kindHint = annotationsByExchange[ExchangeId(row.transactionId)]
                        .orEmpty()
                        .asSequence()
                        .mapNotNull { it.document?.kind?.toRequestKindIdOrNull() }
                        .firstOrNull { it != RequestKindId.HTTP }
                        ?: return@map row
                    row.withDescriptor(
                        describeRequestUseCase.execute(
                            RequestDescriptorInput(
                                transportMethod = CanonicalHttpMethod.fromToken(row.method),
                                absoluteUrl = row.fullUrl,
                                semanticKindHint = kindHint,
                            ),
                        ),
                    )
                }
                publishTrafficProjection()
            }
        }
    }

    /**
     * Prepares a protocol-aware breakpoint rule draft from a captured transaction.
     *
     * Semantic detection runs off the presentation thread through the application use case. The
     * dialog opens only after one complete immutable draft is available, avoiding an HTTP-to-protocol
     * visual transition while body inspection finishes.
     *
     * @param transactionId Target transaction UUID.
     */
    fun createBreakpointFromTransaction(transactionId: String) {
        if (uiState.value.transactions.none { it.transactionId == transactionId }) return
        val pendingCandidate = pendingBreakpoints.firstOrNull {
            it.candidate.exchangeId.value == transactionId
        }?.candidate
        breakpointDraftJob?.cancel()
        breakpointDraftJob = viewModelScope.launch {
            val result = withContext(backgroundDispatcher) {
                prepareBreakpointRuleDraftUseCase.execute(
                    exchangeId = ExchangeId(transactionId),
                    pendingCandidate = pendingCandidate,
                )
            }
            val draft = (result as? PrepareBreakpointRuleDraftResult.Found)?.draft ?: return@launch
            _uiState.update { state ->
                state.copy(
                    isBreakpointDialogVisible = true,
                    prefilledBreakpointRule = draft.rule,
                    prefilledBreakpointProtocolValues = draft.protocolValues,
                )
            }
        }
    }

    /**
     * Closes the traffic workspace breakpoint rule dialog.
     */
    fun closeBreakpointDialog() {
        _uiState.update {
            it.copy(
                isBreakpointDialogVisible = false,
                prefilledBreakpointRule = null,
                prefilledBreakpointProtocolValues = emptyList(),
            )
        }
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
        )
    }

    private companion object {
        private const val TRAFFIC_PAGE_SIZE = 200
        private const val MAX_TRAFFIC_ROWS = 1_000
        private const val MAX_PREPARED_INSPECTOR_ENTRIES = 8
        private const val MAX_PREPARED_INSPECTOR_BYTES = 16L * 1_024L * 1_024L
        private const val MAXIMUM_ANNOTATION_OBSERVATION_ROWS = 900
    }

    /** Purpose of a persisted page query and its bounded-window merge policy. */
    private enum class TrafficPageLoadMode {
        REPLACE,
        REFRESH_NEWEST,
        LOAD_OLDER,
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

private fun String.toRequestKindIdOrNull(): RequestKindId? =
    runCatching { RequestKindId(trim().lowercase()) }.getOrNull()

private fun MethodFilter.toCanonicalMethods(): Set<CanonicalHttpMethod> = when (this) {
    MethodFilter.ALL -> emptySet()
    else -> setOf(CanonicalHttpMethod.fromToken(name))
}

private fun StatusFilter.toCanonicalStatuses(): Set<CanonicalHttpStatus> =
    range?.map(::CanonicalHttpStatus)?.toSet().orEmpty()

private fun ProtocolFilter.toCanonicalSchemes(): Set<HttpScheme> = when (this) {
    ProtocolFilter.HTTP -> setOf(HttpScheme.Standard(StandardHttpScheme.HTTP))
    ProtocolFilter.HTTPS -> setOf(HttpScheme.Standard(StandardHttpScheme.HTTPS))
    ProtocolFilter.ALL,
    ProtocolFilter.HTTP_2,
    ProtocolFilter.HTTP_3 -> emptySet()
}

private fun ProtocolFilter.toCanonicalProtocols(): Set<ApplicationProtocol> = when (this) {
    ProtocolFilter.HTTP_2 -> setOf(ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2))
    ProtocolFilter.HTTP_3 -> setOf(ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_3))
    ProtocolFilter.ALL,
    ProtocolFilter.HTTP,
    ProtocolFilter.HTTPS -> emptySet()
}

private fun ProtocolFilter.matches(row: TrafficRowUiState): Boolean = when (this) {
    ProtocolFilter.ALL -> true
    ProtocolFilter.HTTP -> row.scheme == HttpScheme.Standard(StandardHttpScheme.HTTP)
    ProtocolFilter.HTTPS -> row.scheme == HttpScheme.Standard(StandardHttpScheme.HTTPS)
    ProtocolFilter.HTTP_2 -> row.clientProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2) ||
        row.upstreamProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2)
    ProtocolFilter.HTTP_3 -> row.clientProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_3) ||
        row.upstreamProtocol == ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_3)
}
