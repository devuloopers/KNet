package com.devuloopers.knet.ui.desktop.workspace.model

/**
 * Sealed interface representing UI state for `:ui:desktop:workspace`.
 */
public sealed interface WorkspaceState {
    public data object Loading : WorkspaceState

    public data class Success(
        val activeExplorer: ExplorerType = ExplorerType.COLLECTIONS,
        val selection: WorkspaceSelection? = null,
        val searchQuery: String = "",
        val layout: WorkspaceLayoutData = WorkspaceLayoutData(),
        val expandedNodes: Set<String> = emptySet()
    ) : WorkspaceState
}
