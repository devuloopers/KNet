package com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.usecase.*
import com.devuloopers.knet.ui.desktop.breakpointmanager.mapper.toDomainRule
import com.devuloopers.knet.ui.desktop.breakpointmanager.mapper.toUiModel
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.BreakpointManagerState
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.BreakpointRuleUiModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing presentation state and domain UseCase interactions for Breakpoint Manager Screen and Live Intercept Drawer.
 *
 * All rule mutations and in-flight interception operations are delegated strictly to domain UseCases from `:core:domain`.
 */
class BreakpointManagerViewModel(
    getRulesUseCase: GetRulesUseCase,
    observeGlobalInterceptionUseCase: ObserveGlobalInterceptionUseCase,
    observeActiveInterceptionsUseCase: ObserveActiveInterceptionsUseCase,
    private val saveRuleUseCase: SaveRuleUseCase,
    private val toggleRuleUseCase: ToggleRuleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val toggleGlobalInterceptionUseCase: ToggleGlobalInterceptionUseCase,
    private val forwardInterceptedRequestUseCase: ForwardInterceptedRequestUseCase,
    private val forwardInterceptedResponseUseCase: ForwardInterceptedResponseUseCase,
    private val dropInterceptedTransactionUseCase: DropInterceptedTransactionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreakpointManagerState())
    val uiState: StateFlow<BreakpointManagerState> = _uiState.asStateFlow()

    init {
        getRulesUseCase()
            .onEach { rules ->
                val uiRules = rules.map { it.toUiModel() }
                _uiState.update { it.copy(rules = uiRules) }
            }
            .launchIn(viewModelScope)

        observeGlobalInterceptionUseCase()
            .onEach { isGlobalEnabled ->
                _uiState.update { it.copy(isGlobalInterceptionEnabled = isGlobalEnabled) }
            }
            .launchIn(viewModelScope)

        // Reactive stream of active suspended in-flight HTTP connections via domain UseCase
        observeActiveInterceptionsUseCase()
            .onEach { events ->
                _uiState.update { current ->
                    val activeEv = events.firstOrNull()
                    current.copy(
                        activeEvents = events,
                        activeEvent = activeEv
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun forwardRequest(transactionId: String, modifiedRequest: HttpRequest) {
        viewModelScope.launch {
            forwardInterceptedRequestUseCase(transactionId, modifiedRequest)
        }
    }

    fun forwardResponse(transactionId: String, modifiedResponse: HttpResponse) {
        viewModelScope.launch {
            forwardInterceptedResponseUseCase(transactionId, modifiedResponse)
        }
    }

    fun dropEvent(transactionId: String) {
        viewModelScope.launch {
            dropInterceptedTransactionUseCase(transactionId)
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
        _uiState.update { it.copy(isAddEditDialogVisible = true, editingRule = null) }
    }

    fun openEditDialog(rule: BreakpointRuleUiModel) {
        _uiState.update { it.copy(isAddEditDialogVisible = true, editingRule = rule) }
    }

    fun closeDialog() {
        _uiState.update { it.copy(isAddEditDialogVisible = false, editingRule = null) }
    }

    fun saveRule(
        urlPattern: String,
        method: HttpMethod?,
        phase: BreakpointPhase,
        enabled: Boolean,
        protocolCriteria: com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria = com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.HttpDefault
    ) {
        val currentEditing = _uiState.value.editingRule
        val targetId = currentEditing?.id ?: @OptIn(kotlin.uuid.ExperimentalUuidApi::class) kotlin.uuid.Uuid.random().toString()

        val uiModel = BreakpointRuleUiModel(
            id = targetId,
            urlPattern = urlPattern,
            method = method,
            phase = phase,
            enabled = enabled,
            protocolCriteria = protocolCriteria
        )

        viewModelScope.launch {
            saveRuleUseCase.execute(uiModel.toDomainRule())
            _uiState.update { state ->
                val existingIndex = state.rules.indexOfFirst { it.id == targetId }
                val newRules = if (existingIndex >= 0) {
                    state.rules.toMutableList().apply { set(existingIndex, uiModel) }
                } else {
                    state.rules + uiModel
                }
                state.copy(rules = newRules, isAddEditDialogVisible = false, editingRule = null)
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
