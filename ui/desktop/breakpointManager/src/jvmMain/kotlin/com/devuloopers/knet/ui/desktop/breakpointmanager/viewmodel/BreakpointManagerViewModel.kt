package com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.rules.usecase.*
import com.devuloopers.knet.engine.interceptor.BreakpointPhase
import com.devuloopers.knet.ui.desktop.breakpointmanager.mapper.toDomainRule
import com.devuloopers.knet.ui.desktop.breakpointmanager.mapper.toUiModel
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.BreakpointManagerState
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.BreakpointRuleUiModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel managing presentation state and domain UseCase interactions for Breakpoint Manager Screen.
 *
 * All rule mutations are delegated strictly to domain UseCases from `:core:domain`.
 */
class BreakpointManagerViewModel(
    getRulesUseCase: GetRulesUseCase,
    observeGlobalInterceptionUseCase: ObserveGlobalInterceptionUseCase,
    private val saveRuleUseCase: SaveRuleUseCase,
    private val toggleRuleUseCase: ToggleRuleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val toggleGlobalInterceptionUseCase: ToggleGlobalInterceptionUseCase
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
        enabled: Boolean
    ) {
        val currentEditing = _uiState.value.editingRule
        val targetId = currentEditing?.id ?: UUID.randomUUID().toString()

        val uiModel = BreakpointRuleUiModel(
            id = targetId,
            urlPattern = urlPattern,
            method = method,
            phase = phase,
            enabled = enabled
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
