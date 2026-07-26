package com.devuloopers.knet.ui.rules.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.rules.model.RulesIntent
import com.devuloopers.knet.domain.rules.model.RulesUiState
import com.devuloopers.knet.domain.rules.usecase.GetRulesUseCase
import com.devuloopers.knet.domain.rules.usecase.SaveRuleUseCase
import com.devuloopers.knet.domain.rules.usecase.ToggleRuleUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing UDF for the Rules feature.
 * Strictly adheres to UI -> ViewModel -> UseCase -> Repository flow contract.
 * ViewModels do NOT call Repository methods directly.
 *
 * @property getRulesUseCase UseCase observing rules list off-thread.
 * @property toggleRuleUseCase UseCase toggling rule enabled state.
 * @property saveRuleUseCase UseCase persisting rule updates.
 */
class RulesViewModel(
    private val getRulesUseCase: GetRulesUseCase,
    private val toggleRuleUseCase: ToggleRuleUseCase,
    private val saveRuleUseCase: SaveRuleUseCase
) : ViewModel() {

    private val _activeTab = MutableStateFlow("Rules")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RulesUiState> = _activeTab.flatMapLatest { tab ->
        getRulesUseCase.execute(activeTab = tab)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RulesUiState.Loading
    )

    /**
     * Entry point for processing user actions adhering to Unidirectional Data Flow (UDF).
     *
     * @param intent The user action event.
     */
    fun processIntent(intent: RulesIntent) {
        when (intent) {
            is RulesIntent.ToggleRule -> {
                viewModelScope.launch {
                    toggleRuleUseCase.execute(intent.ruleId, intent.enabled)
                }
            }
            is RulesIntent.SaveRule -> {
                viewModelScope.launch {
                    saveRuleUseCase.execute(intent.rule)
                }
            }
            is RulesIntent.SelectTab -> {
                _activeTab.value = intent.tabName
            }
            is RulesIntent.DeleteRule -> {
                // Delete logic
            }
        }
    }
}
