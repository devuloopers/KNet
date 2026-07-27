package com.devuloopers.knet.ui.workspace.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import com.devuloopers.knet.ui.workspace.model.WorkspaceIntent
import com.devuloopers.knet.ui.workspace.model.WorkspaceUiState
import com.devuloopers.knet.widgets.WidgetType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing workspace layout settings via Unidirectional Data Flow (UDF).
 * Reactively subscribes to DataStore preferences via [GetWorkspaceLayoutUseCase] and persists
 * user changes via [SaveWorkspaceLayoutUseCase].
 *
 * @property getWorkspaceLayoutUseCase UseCase for reading layout settings.
 * @property saveWorkspaceLayoutUseCase UseCase for saving layout settings.
 */
class WorkspaceViewModel(
    private val getWorkspaceLayoutUseCase: GetWorkspaceLayoutUseCase,
    private val saveWorkspaceLayoutUseCase: SaveWorkspaceLayoutUseCase
) : ViewModel() {

    val uiState: StateFlow<WorkspaceUiState> = getWorkspaceLayoutUseCase.execute()
        .map { settings ->
            WorkspaceUiState.Success(
                visibleWidgets = mapOf(
                    WidgetType.TRAFFIC_FEED to settings.isTrafficFeedVisible,
                    WidgetType.INSPECTOR to settings.isInspectorVisible,
                    WidgetType.RULES_CONSOLE to settings.isRulesConsoleVisible,
                    WidgetType.QUICK_REPLAY to settings.isQuickReplayVisible,
                    WidgetType.NOTES_TAGS to settings.isNotesTagsVisible
                ),
                trafficFeedWidthDp = settings.trafficFeedWidthDp,
                sidebarWidthDp = settings.sidebarWidthDp,
                bottomTrayHeightDp = settings.bottomTrayHeightDp
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WorkspaceUiState.Loading
        )

    /**
     * Processes user layout actions following UDF.
     *
     * @param intent User intent.
     */
    fun processIntent(intent: WorkspaceIntent) {
        val currentState = uiState.value as? WorkspaceUiState.Success ?: return

        viewModelScope.launch {
            when (intent) {
                is WorkspaceIntent.ToggleWidget -> {
                    val currentVisible = currentState.visibleWidgets[intent.widget] ?: true
                    val updatedWidgets = currentState.visibleWidgets + (intent.widget to !currentVisible)
                    saveSettingsFromState(currentState.copy(visibleWidgets = updatedWidgets))
                }

                is WorkspaceIntent.UpdateTrafficFeedWidth -> {
                    saveSettingsFromState(currentState.copy(trafficFeedWidthDp = intent.widthDp))
                }

                is WorkspaceIntent.UpdateSidebarWidth -> {
                    saveSettingsFromState(currentState.copy(sidebarWidthDp = intent.widthDp))
                }

                is WorkspaceIntent.UpdateBottomTrayHeight -> {
                    saveSettingsFromState(currentState.copy(bottomTrayHeightDp = intent.heightDp))
                }
            }
        }
    }

    private suspend fun saveSettingsFromState(state: WorkspaceUiState.Success) {
        saveWorkspaceLayoutUseCase.execute(
            WorkspaceLayoutSettings(
                isTrafficFeedVisible = state.visibleWidgets[WidgetType.TRAFFIC_FEED] ?: true,
                isInspectorVisible = state.visibleWidgets[WidgetType.INSPECTOR] ?: true,
                isRulesConsoleVisible = state.visibleWidgets[WidgetType.RULES_CONSOLE] ?: false,
                isQuickReplayVisible = state.visibleWidgets[WidgetType.QUICK_REPLAY] ?: false,
                isNotesTagsVisible = state.visibleWidgets[WidgetType.NOTES_TAGS] ?: false,
                trafficFeedWidthDp = state.trafficFeedWidthDp,
                sidebarWidthDp = state.sidebarWidthDp,
                bottomTrayHeightDp = state.bottomTrayHeightDp
            )
        )
    }
}
