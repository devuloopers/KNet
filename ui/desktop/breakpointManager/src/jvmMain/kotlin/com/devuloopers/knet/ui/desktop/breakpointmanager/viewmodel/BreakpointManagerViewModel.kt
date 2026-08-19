package com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.port.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.application.port.breakpoint.BreakpointResponseEdit
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.usecase.breakpoint.ClearPendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ResolveBreakpointUseCase
import com.devuloopers.knet.application.usecase.breakpoint.BreakpointProtocolRuleUseCase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.usecase.*
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.BreakpointManagerState
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.ResolvedInterceptPayload
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel managing presentation state and domain UseCase interactions for Breakpoint Manager Screen and Live Intercept Drawer.
 *
 * All rule mutations and in-flight interception operations are delegated strictly to domain UseCases from `:core:domain`.
 */
class BreakpointManagerViewModel(
    getRulesUseCase: GetRulesUseCase,
    observeGlobalInterceptionUseCase: ObserveGlobalInterceptionUseCase,
    observePendingBreakpointsUseCase: ObservePendingBreakpointsUseCase,
    private val saveRuleUseCase: SaveRuleUseCase,
    private val toggleRuleUseCase: ToggleRuleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val toggleGlobalInterceptionUseCase: ToggleGlobalInterceptionUseCase,
    private val resolveBreakpointUseCase: ResolveBreakpointUseCase,
    private val clearPendingBreakpointsUseCase: ClearPendingBreakpointsUseCase,
    private val breakpointProtocolRuleUseCase: BreakpointProtocolRuleUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BreakpointManagerState(
            protocolDefinitions = breakpointProtocolRuleUseCase.definitions(),
        ),
    )
    val uiState: StateFlow<BreakpointManagerState> = _uiState.asStateFlow()

    init {
        getRulesUseCase()
            .onEach { rules ->
                _uiState.update { it.copy(rules = rules) }
            }
            .launchIn(viewModelScope)

        observeGlobalInterceptionUseCase()
            .onEach { isGlobalEnabled ->
                _uiState.update { it.copy(isGlobalInterceptionEnabled = isGlobalEnabled) }
            }
            .launchIn(viewModelScope)

        // Reactive stream of active suspended in-flight HTTP connections via domain UseCase.
        // For each new transaction that has not yet been resolved, payload decoding and format
        // detection are dispatched off-thread (Dispatchers.Default) via PayloadResolver so
        // that LiveInterceptDrawer receives pre-computed ResolvedInterceptPayload objects.
        observePendingBreakpointsUseCase.execute()
            .onEach { events ->
                val currentPayloads = _uiState.value.resolvedPayloads
                val activeIds = events.map { it.id }.toSet()

                // Prune entries for transactions that have left the queue.
                val prunedPayloads = currentPayloads.filterKeys { it in activeIds }.toMutableMap()

                // Resolve payloads for any new transactions not yet in the cache.
                val newTransactions = events.filter { it.id !in prunedPayloads }
                if (newTransactions.isNotEmpty()) {
                    val freshResolved = withContext(ioDispatcher) {
                        newTransactions.associate { tx ->
                            val requestBodySpec = PayloadInspectionSpec.fromBytes(
                                body = tx.candidate.requestBody?.copyBytes(),
                                headers = tx.candidate.request.head.headers.map { it.name.value to it.value }
                            )
                            val responseBodySpec = tx.candidate.response?.let { response ->
                                PayloadInspectionSpec.fromBytes(
                                    body = tx.candidate.responseBody?.copyBytes(),
                                    headers = response.head.headers.map { it.name.value to it.value }
                                )
                            } ?: PayloadInspectionSpec.EMPTY
                            tx.id to ResolvedInterceptPayload(
                                transactionId = tx.id,
                                requestPayloadSpec = requestBodySpec,
                                responsePayloadSpec = responseBodySpec
                            )
                        }
                    }
                    prunedPayloads.putAll(freshResolved)
                }

                _uiState.update { current ->
                    val stillSelected = events.find { it.id == current.activeEvent?.id }
                    val activeEv = stillSelected ?: events.firstOrNull()
                    current.copy(
                        activeEvents = events,
                        activeEvent = activeEv,
                        resolvedPayloads = prunedPayloads
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Selects a specific active suspended transaction from the queue to display in the editor.
     *
     * @param eventId Unique identifier of the transaction to focus.
     */
    fun selectActiveEvent(eventId: String) {
        _uiState.update { current ->
            val selected = current.activeEvents.find { it.id == eventId }
            if (selected != null) {
                current.copy(activeEvent = selected)
            } else {
                current
            }
        }
    }

    /**
     * Drops and terminates all active in-flight suspended connections in the queue immediately.
     */
    fun dropAllEvents() {
        viewModelScope.launch {
            clearPendingBreakpointsUseCase.execute()
        }
    }

    fun forwardRequest(transactionId: String, modifiedRequest: BreakpointRequestEdit) {
        viewModelScope.launch {
            resolveBreakpointUseCase.resumeRequest(transactionId, modifiedRequest)
        }
    }

    fun forwardResponse(transactionId: String, modifiedResponse: BreakpointResponseEdit) {
        viewModelScope.launch {
            resolveBreakpointUseCase.resumeResponse(transactionId, modifiedResponse)
        }
    }

    fun dropEvent(transactionId: String) {
        viewModelScope.launch {
            resolveBreakpointUseCase.drop(transactionId)
        }
    }

    fun disableMatchingRule(ruleId: String) {
        if (ruleId.isNotBlank()) {
            toggleRuleStatus(ruleId)
        }
        val currentEvent = _uiState.value.activeEvent
        if (currentEvent != null) {
            dropEvent(currentEvent.id)
        }
    }

    fun dismissCurrentEvent() {
        val currentEvent = _uiState.value.activeEvent
        if (currentEvent != null) {
            dropEvent(currentEvent.id)
        }
    }

    fun toggleGlobalInterception(enabled: Boolean) {
        viewModelScope.launch {
            toggleGlobalInterceptionUseCase(enabled)
            _uiState.update { it.copy(isGlobalInterceptionEnabled = enabled) }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleRuleStatus(ruleId: String) {
        val targetRule = _uiState.value.rules.find { it.id == ruleId } ?: return
        val updated = targetRule.copy(enabled = !targetRule.enabled)
        viewModelScope.launch {
            toggleRuleUseCase(ruleId, updated.enabled)
            _uiState.update { state ->
                state.copy(rules = state.rules.map { if (it.id == ruleId) updated else it })
            }
        }
    }

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                isAddEditDialogVisible = true,
                editingRule = null,
                editingProtocolValues = emptyList(),
            )
        }
    }

    fun openEditDialog(rule: BreakpointRule) {
        _uiState.update {
            it.copy(
                isAddEditDialogVisible = true,
                editingRule = rule,
                editingProtocolValues = breakpointProtocolRuleUseCase.editorValues(rule.protocolCriteria),
            )
        }
    }

    fun closeDialog() {
        _uiState.update {
            it.copy(
                isAddEditDialogVisible = false,
                editingRule = null,
                editingProtocolValues = emptyList(),
            )
        }
    }

    fun saveRule(
        urlPattern: String,
        method: HttpMethod?,
        phase: BreakpointPhase,
        enabled: Boolean,
        protocolId: BreakpointProtocolId = BreakpointProtocolId.HTTP,
        protocolValues: List<ProtocolCriteriaValue> = emptyList(),
    ) {
        val protocolCriteria = breakpointProtocolRuleUseCase.createCriteria(protocolId, protocolValues)
            ?: return
        val currentEditing = _uiState.value.editingRule
        val targetId = currentEditing?.id ?: @OptIn(kotlin.uuid.ExperimentalUuidApi::class) kotlin.uuid.Uuid.random().toString()

        val rule = BreakpointRule(
            id = targetId,
            name = urlPattern,
            urlPattern = urlPattern,
            method = method,
            phase = phase,
            enabled = enabled,
            protocolCriteria = protocolCriteria
        )

        viewModelScope.launch {
            saveRuleUseCase.execute(rule)
            _uiState.update { state ->
                val existingIndex = state.rules.indexOfFirst { it.id == targetId }
                val newRules = if (existingIndex >= 0) {
                    state.rules.toMutableList().apply { set(existingIndex, rule) }
                } else {
                    state.rules + rule
                }
                state.copy(
                    rules = newRules,
                    isAddEditDialogVisible = false,
                    editingRule = null,
                    editingProtocolValues = emptyList(),
                )
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            deleteRuleUseCase(ruleId)
            _uiState.update { state ->
                state.copy(rules = state.rules.filterNot { it.id == ruleId })
            }
        }
    }
}
