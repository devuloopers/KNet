package com.devuloopers.knet.ui.desktop.workspace.model

/**
 * Data representation model for generic workspace panels.
 */
public data class WorkspacePanel(
    val id: String,
    val title: String,
    val isVisible: Boolean = true
)
