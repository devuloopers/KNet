package com.devuloopers.knet.ui.livetraffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.livetraffic.model.LiveTrafficIntent
import com.devuloopers.knet.domain.livetraffic.model.LiveTrafficUiState
import com.devuloopers.knet.domain.livetraffic.model.ProtocolFilter
import com.devuloopers.knet.domain.livetraffic.usecase.ClearLiveTrafficUseCase
import com.devuloopers.knet.domain.livetraffic.usecase.GetLiveTrafficUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel managing Unidirectional Data Flow (UDF) for the Live Traffic feature.
 * Subscribes to database stream via [GetLiveTrafficUseCase], applies off-thread filtering,
 * and exposes an immutable [StateFlow] of [LiveTrafficUiState].
 *
 * @property getLiveTrafficUseCase UseCase filtering and mapping stream off-thread.
 * @property clearLiveTrafficUseCase UseCase clearing live traffic session data.
 */
class LiveTrafficViewModel(
    private val getLiveTrafficUseCase: GetLiveTrafficUseCase,
    private val clearLiveTrafficUseCase: ClearLiveTrafficUseCase
) : ViewModel() {

    private val _activeFilter = MutableStateFlow(ProtocolFilter.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LiveTrafficUiState> = combine(
        _activeFilter,
        _searchQuery,
        _selectedId
    ) { filter, query, selectedId ->
        Triple(filter, query, selectedId)
    }.flatMapLatest { (filter, query, selectedId) ->
        getLiveTrafficUseCase.execute(
            filter = filter,
            searchQuery = query,
            selectedId = selectedId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LiveTrafficUiState.Loading
    )

    /**
     * Entry point for processing user actions adhering to Unidirectional Data Flow (UDF).
     *
     * @param intent The user action event.
     */
    fun processIntent(intent: LiveTrafficIntent) {
        when (intent) {
            is LiveTrafficIntent.SelectProtocol -> {
                _activeFilter.value = intent.filter
            }
            is LiveTrafficIntent.SearchQueryChanged -> {
                _searchQuery.value = intent.query
            }
            is LiveTrafficIntent.SelectTransaction -> {
                _selectedId.value = intent.transactionId
            }
            is LiveTrafficIntent.ClearTraffic -> {
                clearLiveTrafficUseCase.execute()
                _selectedId.value = null
            }
        }
    }
}
