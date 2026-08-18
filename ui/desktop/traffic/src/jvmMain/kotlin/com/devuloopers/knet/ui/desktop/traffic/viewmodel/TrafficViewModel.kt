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
import com.devuloopers.knet.application.port.inspection.ObserveInspectionAnnotationsUseCase
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
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
import com.devuloopers.knet.domain.rules.usecase.SaveRuleUseCase
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
    private val loadTrafficExchangeDetailsUseCase: LoadTrafficExchangeDetailsUseCase,
    observeLocalIpUseCase: ObserveLocalIpUseCase,
    private val getWorkspaceLayoutUseCase: GetWorkspaceLayoutUseCase,
    private val prepareTrafficRequestUseCase: PrepareTrafficRequestUseCase,
    private val observeInspectionAnnotationsUseCase: ObserveInspectionAnnotationsUseCase,
    observeRulesUseCase: ObserveRulesUseCase,
    private val saveRuleUseCase: SaveRuleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<TrafficState> = _uiState.asStateFlow()

    private val preparedStateCache = object : LinkedHashMap<String, InspectorPreparedState>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, InspectorPreparedState>?): Boolean {
            return size > 64
        }
    }

    private val _isCapturing = MutableStateFlow(false)
    private val pageLoadMutex = Mutex()
    private var filterRefreshJob: Job? = null

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

        // Reactive Dynamic Proxy Port Re-binding via flatMapLatest
        viewModelScope.launch {
            _isCapturing.flatMapLatest { isCapturing ->
                if (isCapturing) {
                    val portFlow = getWorkspaceLayoutUseCase.execute()
                        .map { it.proxyPort }
                        .distinctUntilChanged()

                    flow {
                        var isFirst = true
                        portFlow.collect { port ->
                            if (!isFirst) {
                                delay(500.milliseconds)
                            }
                            isFirst = false
                            emit(port)
                        }
                    }
                } else {
                    emptyFlow()
                }
            }.collect { port ->
                if (_uiState.value.engineState !is ProxyRuntimeState.Stopped) {
                    stopProxyRuntimeUseCase.execute()
                }
                startLoopbackProxyUseCase.execute(port)
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
            observeProxyRuntimeStateUseCase.execute().collect { runtimeState ->
                _uiState.update { current ->
                    val capState = if (runtimeState is ProxyRuntimeState.Running) {
                        CaptureState.CAPTURING
                    } else {
                        CaptureState.STOPPED
                    }
                    val errorMessage = when (runtimeState) {
                        is ProxyRuntimeState.Failed -> runtimeState.code
                        is ProxyRuntimeState.Running,
                        ProxyRuntimeState.Starting -> null
                        else -> current.engineErrorMessage
                    }
                    current.copy(
                        engineState = runtimeState,
                        captureState = capState,
                        engineErrorMessage = errorMessage
                    )
                }
            }
        }

        // 2. Observe Reactive Host Local IP Address
        viewModelScope.launch {
            observeLocalIpUseCase.execute().collect { ip ->
                _uiState.update { current ->
                    current.copy(localIpAddress = ip)
                }
            }
        }

        // 3. Observe only a compact session ID and store generations; rows are fetched as bounded keyset pages.
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
                _isCapturing.value = true
            }

            is TrafficIntent.StopCapture -> {
                _isCapturing.value = false
                viewModelScope.launch {
                    stopProxyRuntimeUseCase.execute()
                }
            }

            is TrafficIntent.ClearFeed -> {
                viewModelScope.launch {
                    clearTrafficHistoryUseCase.execute()
                    synchronized(preparedStateCache) {
                        preparedStateCache.clear()
                    }
                    _uiState.update {
                        it.copy(
                            transactions = emptyList(),
                            filteredTransactions = emptyList(),
                            selectedTransactionId = null,
                            preparedState = InspectorPreparedState(),
                        )
                    }
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
                _uiState.update { current ->
                    val filtered = applyFilters(
                        transactions = current.transactions,
                        query = intent.query,
                        protocol = current.selectedProtocolFilter,
                        method = current.selectedMethodFilter,
                        status = current.selectedStatusFilter
                    )
                    current.copy(
                        searchQuery = intent.query,
                        filteredTransactions = filtered
                    )
                }
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByProtocol -> {
                _uiState.update { current ->
                    val filtered = applyFilters(
                        transactions = current.transactions,
                        query = current.searchQuery,
                        protocol = intent.protocol,
                        method = current.selectedMethodFilter,
                        status = current.selectedStatusFilter
                    )
                    current.copy(
                        selectedProtocolFilter = intent.protocol,
                        filteredTransactions = filtered
                    )
                }
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByMethod -> {
                _uiState.update { current ->
                    val filtered = applyFilters(
                        transactions = current.transactions,
                        query = current.searchQuery,
                        protocol = current.selectedProtocolFilter,
                        method = intent.method,
                        status = current.selectedStatusFilter
                    )
                    current.copy(
                        selectedMethodFilter = intent.method,
                        filteredTransactions = filtered
                    )
                }
                scheduleFilteredPageRefresh()
            }

            is TrafficIntent.FilterByStatus -> {
                _uiState.update { current ->
                    val filtered = applyFilters(
                        transactions = current.transactions,
                        query = current.searchQuery,
                        protocol = current.selectedProtocolFilter,
                        method = current.selectedMethodFilter,
                        status = intent.status
                    )
                    current.copy(
                        selectedStatusFilter = intent.status,
                        filteredTransactions = filtered
                    )
                }
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

    private fun scheduleFilteredPageRefresh() {
        filterRefreshJob?.cancel()
        filterRefreshJob = viewModelScope.launch {
            delay(150.milliseconds)
            loadTrafficPage(reset = true)
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
                val refreshedRows = page.items.mapIndexed { index, snapshot ->
                    snapshot.toTrafficRowUiState(index + 1)
                }
                val refreshedIds = refreshedRows.asSequence().map { it.transactionId }.toHashSet()
                val rows = (refreshedRows + latestState.transactions.filterNot {
                    it.transactionId in refreshedIds
                })
                    .sortedWith(
                        compareByDescending<TrafficRowUiState> { it.timestamp }
                            .thenByDescending { it.transactionId },
                    )
                    .take(MAX_TRAFFIC_ROWS)
                    .mapIndexed { index, row -> row.copy(id = index + 1) }
                val filtersStillMatch = latestState.searchQuery == before.searchQuery &&
                    latestState.selectedProtocolFilter == before.selectedProtocolFilter &&
                    latestState.selectedMethodFilter == before.selectedMethodFilter &&
                    latestState.selectedStatusFilter == before.selectedStatusFilter
                if (!filtersStillMatch) return
                val selectedId = latestState.selectedTransactionId
                    ?.takeIf { id -> rows.any { row -> row.transactionId == id } }
                    ?: rows.firstOrNull()?.transactionId
                _uiState.update { current ->
                    current.copy(
                        transactions = rows,
                        filteredTransactions = applyFilters(
                            rows,
                            current.searchQuery,
                            current.selectedProtocolFilter,
                            current.selectedMethodFilter,
                            current.selectedStatusFilter,
                        ),
                        nextPageCursor = page.nextCursor.takeIf { rows.size < MAX_TRAFFIC_ROWS },
                        pageGeneration = maxOf(current.pageGeneration, page.generation),
                        selectedTransactionId = selectedId,
                    )
                }
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

    /**
     * Saves a breakpoint rule configured via the Traffic Inspector pre-populated dialog.
     */
    fun saveBreakpointRule(
        urlPattern: String,
        method: CanonicalHttpMethod?,
        phaseType: com.devuloopers.knet.domain.rules.model.BreakpointPhase,
        enabled: Boolean,
        protocolCriteria: com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
    ) {
        val editingId = _uiState.value.prefilledBreakpointRule?.id ?: Uuid.random().toString()
        val rule = BreakpointRule(
            id = editingId,
            name = urlPattern,
            phase = phaseType,
            urlPattern = urlPattern,
            method = method,
            enabled = enabled,
            protocolCriteria = protocolCriteria
        )
        viewModelScope.launch {
            saveRuleUseCase.execute(rule)
            closeBreakpointDialog()
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
            previewFormatMode = PreviewFormatMode.PRETTY
        )
    }

    private companion object {
        private const val TRAFFIC_PAGE_SIZE = 200
        private const val MAX_TRAFFIC_ROWS = 1_000
    }
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
