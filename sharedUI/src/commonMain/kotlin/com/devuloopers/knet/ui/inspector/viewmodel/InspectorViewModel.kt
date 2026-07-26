package com.devuloopers.knet.ui.inspector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.inspector.model.InspectorIntent
import com.devuloopers.knet.domain.inspector.model.InspectorTab
import com.devuloopers.knet.domain.inspector.model.InspectorUiState
import com.devuloopers.knet.domain.inspector.usecase.GetTransactionDetailUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel managing UDF for the Inspector feature.
 * Strictly adheres to UI -> ViewModel -> UseCase -> Repository flow contract.
 * ViewModels do NOT call Repository methods directly.
 *
 * @property getTransactionDetailUseCase UseCase fetching transaction details off-thread.
 */
class InspectorViewModel(
    private val getTransactionDetailUseCase: GetTransactionDetailUseCase
) : ViewModel() {

    private val _selectedTransactionId = MutableStateFlow<String?>(null)
    private val _activeTab = MutableStateFlow(InspectorTab.OVERVIEW)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<InspectorUiState> = combine(
        _selectedTransactionId,
        _activeTab
    ) { id, tab ->
        Pair(id, tab)
    }.flatMapLatest { (id, tab) ->
        getTransactionDetailUseCase.execute(id, tab)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InspectorUiState.NoSelection
    )

    /**
     * Entry point for processing user actions adhering to Unidirectional Data Flow (UDF).
     *
     * @param intent The user action event.
     */
    fun processIntent(intent: InspectorIntent) {
        when (intent) {
            is InspectorIntent.SelectTransaction -> {
                _selectedTransactionId.value = intent.transactionId
            }
            is InspectorIntent.SelectTab -> {
                _activeTab.value = intent.tab
            }
        }
    }
}
