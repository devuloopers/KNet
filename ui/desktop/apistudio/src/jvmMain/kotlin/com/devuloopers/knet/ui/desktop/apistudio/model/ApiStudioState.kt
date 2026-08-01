package com.devuloopers.knet.ui.desktop.apistudio.model

/**
 * Top-level UI state DTO for API Studio.
 */
public data class ApiStudioState(
    val tabs: List<RequestTab> = listOf(RequestTab("tab_1", "New Request")),
    val activeTabId: String = "tab_1",
    val editorState: RequestEditorState = RequestEditorState(),
    val responsePresentation: ResponsePresentation? = null,
    val selectedEnvironment: String = "No Environment",
    val executionState: ExecutionState = ExecutionState.IDLE,
    val errorMessage: String? = null
)
