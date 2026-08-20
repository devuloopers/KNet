package com.devuloopers.knet.ui.desktop.workspace.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.UpdateWorkspaceLayoutUseCase
import com.devuloopers.knet.ui.desktop.workspace.model.ExplorerType
import com.devuloopers.knet.ui.desktop.workspace.model.WorkspaceIntent
import com.devuloopers.knet.ui.desktop.workspace.model.WorkspaceLayoutData
import com.devuloopers.knet.ui.desktop.workspace.model.WorkspaceState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing workspace layout persistence and explorer selection following UDF.
 */
class WorkspaceViewModel(
    private val getWorkspaceLayoutUseCase: GetWorkspaceLayoutUseCase,
    private val updateWorkspaceLayoutUseCase: UpdateWorkspaceLayoutUseCase,
) : ViewModel() {

    private val _activeExplorer = MutableStateFlow(ExplorerType.COLLECTIONS)
    private val _searchQuery = MutableStateFlow("")
    private val _expandedNodes = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<WorkspaceState> = combine(
        flow = getWorkspaceLayoutUseCase.execute(),
        flow2 = _activeExplorer,
        flow3 = _searchQuery,
        flow4 = _expandedNodes
    ) { settings, explorer, query, nodes ->
        WorkspaceState.Success(
            activeExplorer = explorer,
            searchQuery = query,
            expandedNodes = nodes,
            layout = WorkspaceLayoutData(
                explorerWidthDp = settings.trafficFeedWidthDp,
                sidebarWidthDp = settings.sidebarWidthDp,
                bottomTrayHeightDp = settings.bottomTrayHeightDp
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WorkspaceState.Loading
    )

    /**
     * Processes user workspace intents following UDF.
     */
    fun processIntent(intent: WorkspaceIntent) {
        val currentState = uiState.value as? WorkspaceState.Success ?: return

        viewModelScope.launch {
            when (intent) {
                is WorkspaceIntent.SelectExplorer -> {
                    _activeExplorer.value = intent.type
                }

                is WorkspaceIntent.SelectItem -> {
                    // Selection handling
                }

                is WorkspaceIntent.ToggleNode -> {
                    val currentNodes = _expandedNodes.value
                    _expandedNodes.value = if (currentNodes.contains(intent.nodeId)) {
                        currentNodes - intent.nodeId
                    } else {
                        currentNodes + intent.nodeId
                    }
                }

                is WorkspaceIntent.Search -> {
                    _searchQuery.value = intent.query
                }

                is WorkspaceIntent.UpdateExplorerWidth -> {
                    saveSettingsFromState(currentState.copy(layout = currentState.layout.copy(explorerWidthDp = intent.widthDp)))
                }

                is WorkspaceIntent.UpdateSidebarWidth -> {
                    saveSettingsFromState(currentState.copy(layout = currentState.layout.copy(sidebarWidthDp = intent.widthDp)))
                }

                is WorkspaceIntent.UpdateBottomHeight -> {
                    saveSettingsFromState(currentState.copy(layout = currentState.layout.copy(bottomTrayHeightDp = intent.heightDp)))
                }
            }
        }
    }

    private suspend fun saveSettingsFromState(state: WorkspaceState.Success) {
        updateWorkspaceLayoutUseCase.execute { current ->
            current.copy(
                trafficFeedWidthDp = state.layout.explorerWidthDp,
                sidebarWidthDp = state.layout.sidebarWidthDp,
                bottomTrayHeightDp = state.layout.bottomTrayHeightDp,
            )
        }
    }
}
