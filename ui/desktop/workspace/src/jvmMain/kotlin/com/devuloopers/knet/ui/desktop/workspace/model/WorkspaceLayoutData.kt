package com.devuloopers.knet.ui.desktop.workspace.model

/**
 * Data DTO for persisted workspace layout configuration parameters.
 */
public data class WorkspaceLayoutData(
    val explorerWidthDp: Float = 260f,
    val sidebarWidthDp: Float = 340f,
    val bottomTrayHeightDp: Float = 200f,
    val isExplorerExpanded: Boolean = true
)
