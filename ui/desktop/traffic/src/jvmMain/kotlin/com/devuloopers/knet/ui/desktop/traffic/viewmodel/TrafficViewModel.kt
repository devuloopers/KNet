package com.devuloopers.knet.ui.desktop.traffic.viewmodel

import androidx.lifecycle.ViewModel
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficSelection
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficState

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel managing live traffic feed UDF state, filtering, and selection.
 */
public class TrafficViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TrafficState())
    public val uiState: StateFlow<TrafficState> = _uiState.asStateFlow()

    public fun processIntent(intent: TrafficIntent) {
        when (intent) {
            is TrafficIntent.SelectTransaction -> {
                _uiState.update {
                    it.copy(selection = TrafficSelection(selectedIds = setOf(intent.id), primarySelectedId = intent.id))
                }
            }

            is TrafficIntent.MultiSelect -> {
                _uiState.update {
                    it.copy(selection = TrafficSelection(selectedIds = intent.ids, primarySelectedId = intent.ids.firstOrNull()))
                }
            }

            is TrafficIntent.FilterByMethod -> {
                _uiState.update {
                    it.copy(filter = it.filter.copy(method = intent.method))
                }
            }

            is TrafficIntent.FilterByStatus -> {
                _uiState.update {
                    it.copy(filter = it.filter.copy(statusGroup = intent.statusGroup))
                }
            }

            is TrafficIntent.FilterByProtocol -> {
                _uiState.update {
                    it.copy(filter = it.filter.copy(protocol = intent.protocol))
                }
            }

            is TrafficIntent.Search -> {
                _uiState.update {
                    it.copy(filter = it.filter.copy(searchQuery = intent.query))
                }
            }

            TrafficIntent.PauseFeed -> {
                _uiState.update { it.copy(isPaused = true) }
            }

            TrafficIntent.ResumeFeed -> {
                _uiState.update { it.copy(isPaused = false) }
            }

            TrafficIntent.ClearFeed -> {
                _uiState.update {
                    it.copy(transactions = emptyList(), filteredTransactions = emptyList(), selection = TrafficSelection())
                }
            }

            TrafficIntent.ToggleAutoScroll -> {
                _uiState.update { it.copy(autoScroll = !it.autoScroll) }
            }
        }
    }
}
