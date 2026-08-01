package com.devuloopers.knet.ui.desktop.workspace.model

/**
 * Sealed interface representing user intents for `:ui:desktop:workspace`.
 */
sealed interface WorkspaceIntent {
    data class SelectExplorer(val type: ExplorerType) : WorkspaceIntent
    data class SelectItem(val selection: WorkspaceSelection) : WorkspaceIntent
    data class ToggleNode(val nodeId: String) : WorkspaceIntent
    data class Search(val query: String) : WorkspaceIntent
    data class UpdateExplorerWidth(val widthDp: Float) : WorkspaceIntent
    data class UpdateSidebarWidth(val widthDp: Float) : WorkspaceIntent
    data class UpdateBottomHeight(val heightDp: Float) : WorkspaceIntent
}
