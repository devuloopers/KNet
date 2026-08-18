package com.devuloopers.knet.ui.desktop.apistudio.model

/**
 * Top-level UI state DTO for API Studio request authoring, execution, and response inspection.
 *
 * @property tabs List of active open request tabs.
 * @property activeTabId Unique ID of the currently selected request tab.
 * @property editorState Current active request editor configuration state.
 * @property responseInspection Response inspector state, or null if no response was received.
 * @property selectedEnvironment Selected workspace environment name string.
 * @property executionState Current HTTP execution state (IDLE, EXECUTING, SUCCESS, ERROR).
 * @property errorMessage Error message string if execution failed.
 * @property sessionContext Active editing session context (None, UnsavedDraft, SavedRequest).
 */
data class ApiStudioState(
    val tabs: List<RequestTab> = listOf(RequestTab("tab_1", "New Request")),
    val activeTabId: String = "tab_1",
    val editorState: RequestEditorState = RequestEditorState(),
    val responseInspection: ResponseInspectorState? = null,
    val selectedEnvironment: String = "No Environment",
    val executionState: ExecutionState = ExecutionState.IDLE,
    val errorMessage: String? = null,
    val sessionContext: SessionContext = SessionContext.None
)
