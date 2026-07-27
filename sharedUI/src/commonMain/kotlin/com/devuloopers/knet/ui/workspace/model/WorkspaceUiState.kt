package com.devuloopers.knet.ui.workspace.model

import com.devuloopers.knet.widgets.WidgetType

/**
 * Sealed interface representing UI state for the workspace layout.
 */
sealed interface WorkspaceUiState {
    /** Initial loading state. */
    data object Loading : WorkspaceUiState

    /** Successfully loaded workspace layout state. */
    data class Success(
        val visibleWidgets: Map<WidgetType, Boolean>,
        val trafficFeedWidthDp: Float,
        val sidebarWidthDp: Float,
        val bottomTrayHeightDp: Float
    ) : WorkspaceUiState
}
