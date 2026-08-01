package com.devuloopers.knet.ui.desktop.inspector.viewmodel

import androidx.lifecycle.ViewModel
import com.devuloopers.knet.ui.desktop.inspector.model.InspectorIntent
import com.devuloopers.knet.ui.desktop.inspector.model.InspectorState

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel managing read-only inspection state following UDF.
 */
public class InspectorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(InspectorState())
    public val uiState: StateFlow<InspectorState> = _uiState.asStateFlow()

    public fun processIntent(intent: InspectorIntent) {
        when (intent) {
            is InspectorIntent.SelectTab -> {
                _uiState.update { it.copy(activeTab = intent.tab) }
            }

            is InspectorIntent.Search -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
            }

            is InspectorIntent.SelectBodyMode -> {
                _uiState.update { it.copy(bodyMode = intent.mode) }
            }

            is InspectorIntent.SelectTransaction -> {
                _uiState.update { it.copy(overview = intent.overview) }
            }
        }
    }
}
