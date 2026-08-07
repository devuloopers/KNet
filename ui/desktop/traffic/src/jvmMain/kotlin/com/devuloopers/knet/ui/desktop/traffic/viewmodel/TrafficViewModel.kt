package com.devuloopers.knet.ui.desktop.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.domain.proxy.usecase.StartProxyEngineUseCase
import com.devuloopers.knet.domain.proxy.usecase.StopProxyEngineUseCase
import com.devuloopers.knet.domain.traffic.model.*
import com.devuloopers.knet.domain.traffic.usecase.ClearLiveTrafficUseCase
import com.devuloopers.knet.domain.traffic.usecase.GetLiveTrafficUseCase
import com.devuloopers.knet.domain.traffic.usecase.LoadTransactionBodyUseCase
import com.devuloopers.knet.domain.util.decodeBodyToText
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.ui.desktop.codeeditor.service.DocumentPreparationService
import com.devuloopers.knet.ui.desktop.traffic.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing live traffic feed state, proxy engine lifecycle observation, filtering, and inspection selection.
 */
class TrafficViewModel(
    getLiveTrafficUseCase: GetLiveTrafficUseCase? = null,
    private val clearLiveTrafficUseCase: ClearLiveTrafficUseCase? = null,
    private val startProxyEngineUseCase: StartProxyEngineUseCase? = null,
    private val stopProxyEngineUseCase: StopProxyEngineUseCase? = null,
    observeProxyEngineStateUseCase: ObserveProxyEngineStateUseCase? = null,
    private val loadTransactionBodyUseCase: LoadTransactionBodyUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<TrafficState> = _uiState.asStateFlow()

    private var preparationJob: Job? = null
    private val preparedStateCache = object : LinkedHashMap<String, InspectorPreparedState>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, InspectorPreparedState>?): Boolean {
            return size > 64
        }
    }

    init {
        // 1. Observe Proxy Engine State
        observeProxyEngineStateUseCase?.let { useCase ->
            viewModelScope.launch {
                useCase.execute().collect { state ->
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
        }

        // 2. Observe Live Traffic Database Stream — GetLiveTrafficUseCase handles domain filtering.
        getLiveTrafficUseCase?.let { useCase ->
            viewModelScope.launch {
                useCase.execute(ProtocolFilter.ALL, "").conflate().collect { liveState ->
                    when (liveState) {
                        is LiveTrafficUiState.Success -> {
                            _uiState.update { current ->
                                val selectedId =
                                    current.selectedTransactionId ?: liveState.items.firstOrNull()?.transactionId
                                val updated = current.copy(
                                    transactions = liveState.items,
                                    filteredTransactions = liveState.items,
                                    selectedTransactionId = selectedId
                                )
                                val selectedTx = updated.selectedTransaction
                                prepareTransaction(selectedTx)
                                updated
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
    }

    fun processIntent(intent: TrafficIntent) {
        when (intent) {
            is TrafficIntent.StartCapture -> {
                viewModelScope.launch {
                    startProxyEngineUseCase?.execute(StartProxyEngineUseCase.DEFAULT_PORT)
                }
            }

            is TrafficIntent.StopCapture -> {
                viewModelScope.launch {
                    stopProxyEngineUseCase?.execute()
                }
            }

            is TrafficIntent.ClearFeed -> {
                clearLiveTrafficUseCase?.execute()
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
                val targetTx = _uiState.value.selectedTransaction
                prepareTransaction(targetTx)
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

    private fun prepareTransaction(tx: TrafficItemUiState?) {
        if (tx == null) {
            _uiState.update { it.copy(preparedState = InspectorPreparedState()) }
            return
        }

        val cached = synchronized(preparedStateCache) { preparedStateCache[tx.transactionId] }
        if (cached != null) {
            _uiState.update { it.copy(preparedState = cached) }
            return
        }

        preparationJob?.cancel()
        _uiState.update {
            it.copy(
                preparedState = InspectorPreparedState(
                    transactionId = tx.transactionId,
                    isPreparing = true
                )
            )
        }

        preparationJob = viewModelScope.launch(Dispatchers.Default) {
            // Lazily load body bytes from disk only now that the user has selected this row.
            val body = loadTransactionBodyUseCase?.execute(tx.transactionId)

            val reqBodyText = body?.let {
                decodeBodyToText(it.requestBody, it.requestHeaders)
            } ?: ""

            val respBodyText = body?.let {
                decodeBodyToText(it.responseBody, it.responseHeaders)
            } ?: ""

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

            val prepared = InspectorPreparedState(
                transactionId = tx.transactionId,
                requestBody = reqDoc,
                responseBody = respDoc,
                isPreparing = false
            )

            synchronized(preparedStateCache) {
                preparedStateCache[tx.transactionId] = prepared
            }

            _uiState.update { current ->
                if (current.selectedTransactionId == tx.transactionId) {
                    current.copy(preparedState = prepared)
                } else {
                    current
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
