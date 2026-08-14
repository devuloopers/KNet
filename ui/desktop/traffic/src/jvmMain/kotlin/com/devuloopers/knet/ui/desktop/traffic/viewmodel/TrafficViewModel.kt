@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)


package com.devuloopers.knet.ui.desktop.traffic.viewmodel

import com.devuloopers.knet.core.logger.KNetLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.domain.proxy.usecase.StartProxyEngineUseCase
import com.devuloopers.knet.domain.proxy.usecase.StopProxyEngineUseCase
import com.devuloopers.knet.domain.traffic.model.*
import com.devuloopers.knet.domain.traffic.usecase.ClearLiveTrafficUseCase
import com.devuloopers.knet.domain.traffic.usecase.ExportTrafficToSpecUseCase
import com.devuloopers.knet.domain.traffic.usecase.GetLiveTrafficUseCase
import com.devuloopers.knet.domain.traffic.usecase.LoadTransactionBodyUseCase
import com.devuloopers.knet.domain.util.decodeBodyToText
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.ui.desktop.codeeditor.service.DocumentPreparationService
import com.devuloopers.knet.ui.desktop.traffic.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds
import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.usecase.ObserveRulesUseCase
import com.devuloopers.knet.domain.rules.usecase.SaveRuleUseCase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel managing live traffic feed state, proxy engine lifecycle observation, filtering, and inspection selection.
 */
class TrafficViewModel(
    getLiveTrafficUseCase: GetLiveTrafficUseCase,
    private val clearLiveTrafficUseCase: ClearLiveTrafficUseCase,
    private val startProxyEngineUseCase: StartProxyEngineUseCase,
    private val stopProxyEngineUseCase: StopProxyEngineUseCase,
    observeProxyEngineStateUseCase: ObserveProxyEngineStateUseCase,
    private val loadTransactionBodyUseCase: LoadTransactionBodyUseCase,
    observeLocalIpUseCase: ObserveLocalIpUseCase,
    private val getWorkspaceLayoutUseCase: GetWorkspaceLayoutUseCase,
    private val exportTrafficToSpecUseCase: ExportTrafficToSpecUseCase,
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

    /**
     * Asynchronously constructs a pristine, zero-data-loss [NetworkRequestSpec] for the given [transactionId]
     * using [ExportTrafficToSpecUseCase] and invokes [onSpecReady] on the main thread.
     *
     * @param transactionId Unique UUID of the target transaction.
     * @param onSpecReady Callback executed on main thread with the constructed [NetworkRequestSpec].
     */
    fun exportToStudioSpec(
        transactionId: String,
        onSpecReady: (com.devuloopers.knet.domain.network.model.NetworkRequestSpec) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val prepared = _uiState.value.preparedState
            val cachedReqBody = if (prepared.transactionId == transactionId && prepared.requestBody.rawText.isNotBlank()) {
                prepared.requestBody.rawText
            } else {
                null
            }

            val displayedItem = _uiState.value.transactions.find { it.transactionId == transactionId }
            val spec = exportTrafficToSpecUseCase.execute(
                transactionId = transactionId,
                cachedReqBody = cachedReqBody,
                fallbackItem = displayedItem
            )
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
                clearLiveTrafficUseCase.execute()
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
                if (_uiState.value.engineState !is ProxyEngineState.Stopped) {
                    stopProxyEngineUseCase.execute()
                }
                startProxyEngineUseCase.execute(port)
            }
        }

        // Reactive Transaction Payload Preparation via flatMapLatest
        viewModelScope.launch {
            _uiState
                .map { it.selectedTransaction }
                .distinctUntilChanged()
                .flatMapLatest { tx ->
                    flow {
                        if (tx == null) {
                            emit(InspectorPreparedState())
                            return@flow
                        }

                        val isPending = tx.status <= 0
                        val cached = synchronized(preparedStateCache) { preparedStateCache[tx.transactionId] }
                        if (cached != null && (isPending || cached.responseBody.rawText.isNotBlank())) {
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
                            val body = loadTransactionBodyUseCase.execute(tx.transactionId)

                            val reqBodyText = body?.let { decodeBodyToText(it.requestBody, it.requestHeaders) } ?: ""
                            val respBodyText = body?.let { decodeBodyToText(it.responseBody, it.responseHeaders) } ?: ""

                            val reqLang = detectLanguage(tx.requestHeaders["Content-Type"])
                            val reqFormatted = formatPayload(tx.requestHeaders["Content-Type"], reqBodyText)
                            val reqDoc = DocumentPreparationService.prepare(
                                rawText = reqBodyText,
                                formattedText = reqFormatted,
                                language = reqLang
                            )

                            val respLang = detectLanguage(tx.responseHeaders["Content-Type"])
                            val respFormatted = formatPayload(tx.responseHeaders["Content-Type"], respBodyText)
                            val respDoc = DocumentPreparationService.prepare(
                                rawText = respBodyText,
                                formattedText = respFormatted,
                                language = respLang
                            )

                            val state = InspectorPreparedState(
                                transactionId = tx.transactionId,
                                requestBody = reqDoc,
                                responseBody = respDoc,
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
                }
                .collect { prepared ->
                    _uiState.update { it.copy(preparedState = prepared) }
                }
        }

        // 1. Observe Proxy Engine State
        viewModelScope.launch {
            observeProxyEngineStateUseCase.execute().collect { state ->
                _uiState.update { current ->
                    val capState = if (state is ProxyEngineState.Running) {
                        CaptureState.CAPTURING
                    } else {
                        CaptureState.STOPPED
                    }
                    current.copy(
                        engineState = state,
                        captureState = capState
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

        // 3. Observe Room DB Active Breakpoint Rules for Traffic Row Highlighting
        viewModelScope.launch {
            observeRulesUseCase.execute().collect { rules ->
                _uiState.update { current ->
                    current.copy(activeBreakpointRules = rules)
                }
            }
        }

        // 4. Observe Live Traffic Database Stream — GetLiveTrafficUseCase handles domain filtering.
        viewModelScope.launch {
            getLiveTrafficUseCase.execute(ProtocolFilter.ALL, "").conflate().collect { liveState ->
                    when (liveState) {
                        is LiveTrafficUiState.Success -> {
                            _uiState.update { current ->
                                val selectedId =
                                    current.selectedTransactionId ?: liveState.items.firstOrNull()?.transactionId
                                current.copy(
                                    transactions = liveState.items,
                                    filteredTransactions = liveState.items,
                                    selectedTransactionId = selectedId
                                )
                            }
                        }

                        is LiveTrafficUiState.Empty -> {
                            _uiState.update { current ->
                                current.copy(
                                    transactions = emptyList(),
                                    filteredTransactions = emptyList()
                                )
                            }
                        }

                        is LiveTrafficUiState.Loading -> {
                            // Initializing state — no-op
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
                    stopProxyEngineUseCase.execute()
                }
            }

            is TrafficIntent.ClearFeed -> {
                clearLiveTrafficUseCase.execute()
                _uiState.update {
                    it.copy(
                        transactions = emptyList(),
                        filteredTransactions = emptyList(),
                        selectedTransactionId = null
                    )
                }
            }

            is TrafficIntent.ToggleAutoScroll -> {
                _uiState.update { it.copy(autoScroll = !it.autoScroll) }
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

    private fun formatPayload(contentType: String?, bodyText: String): String {
        if (bodyText.isBlank()) return ""
        val trimmed = bodyText.trimEnd()
        val isJson = contentType.isNullOrBlank() || contentType.contains(
            other = "json",
            ignoreCase = true
        ) || trimmed.startsWith("{") || trimmed.startsWith("[")
        return if (isJson) {
            JsonBodyFormatter().prettyPrintJson(trimmed).trimEnd()
        } else {
            trimmed
        }
    }

    private fun detectLanguage(contentType: String?): String {
        if (contentType.isNullOrBlank()) return "json"
        val lower = contentType.lowercase()
        return when {
            lower.contains("json") -> "json"
            lower.contains("html") -> "html"
            lower.contains("xml") -> "xml"
            lower.contains("javascript") || lower.contains("js") -> "js"
            lower.contains("css") -> "css"
            else -> "json"
        }
    }

    private fun applyFilters(
        transactions: List<TrafficItemUiState>,
        query: String,
        protocol: ProtocolFilter,
        method: MethodFilter,
        status: StatusFilter
    ): List<TrafficItemUiState> {
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
     * Constructs a pre-populated [RuleModel] from a captured transaction and opens the Add/Edit dialog.
     *
     * @param transactionId Target transaction UUID.
     */
    fun createBreakpointFromTransaction(transactionId: String) {
        val item = uiState.value.transactions.find { it.transactionId == transactionId } ?: return
        val targetUrl = item.fullUrl

        // Auto-detect GraphQL Metadata
        val (protocolCriteria, ruleUrl) = when (val meta = item.interceptionMetadata) {
            is com.devuloopers.knet.domain.protocol.model.InterceptionMetadata.GraphQL -> {
                com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.GraphQL(operationName = meta.operationName) to targetUrl
            }
            else -> {
                com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.HttpDefault to targetUrl
            }
        }

        val prefilledModel = RuleModel(
            id = Uuid.random().toString(),
            name = ruleUrl,
            type = com.devuloopers.knet.domain.rules.model.BreakpointPhase.BOTH,
            condition = ruleUrl,
            action = item.method,
            enabled = true,
            protocolCriteria = protocolCriteria
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
        method: com.devuloopers.knet.domain.collection.model.HttpMethod?,
        phaseType: com.devuloopers.knet.domain.rules.model.BreakpointPhase,
        enabled: Boolean,
        protocolCriteria: com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
    ) {
        val editingId = _uiState.value.prefilledBreakpointRule?.id ?: Uuid.random().toString()
        val rule = RuleModel(
            id = editingId,
            name = urlPattern,
            type = phaseType,
            condition = urlPattern,
            action = method?.name ?: "ALL",
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
            engineState = ProxyEngineState.Stopped,
            searchQuery = "",
            selectedProtocolFilter = ProtocolFilter.ALL,
            selectedMethodFilter = MethodFilter.ALL,
            selectedStatusFilter = StatusFilter.ALL,
            autoScroll = true,
            activeInspectorTab = InspectorTab.OVERVIEW,
            previewFormatMode = PreviewFormatMode.PRETTY
        )
    }
}
