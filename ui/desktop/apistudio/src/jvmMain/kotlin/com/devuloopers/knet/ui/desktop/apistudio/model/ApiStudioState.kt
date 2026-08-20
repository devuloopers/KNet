package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin

/**
 * Top-level UI state DTO for API Studio request authoring, execution, and response inspection.
 *
 * @property editorState Current active request editor configuration state.
 * @property responseInspection Response inspector state, or null if no response was received.
 * @property executionState Current HTTP execution state (IDLE, EXECUTING, SUCCESS, ERROR).
 * @property errorMessage Error message string if execution failed.
 * @property sessionContext Active editing session context (None, UnsavedDraft, SavedRequest).
 * @property activeDocumentTitle Stable title used by the sidebar and persistence.
 * @property activeDocumentNameOrigin Whether the active title is generated or explicitly user-owned.
 * @property selectedRequestId Stable identifier highlighted in the collections sidebar.
 * @property isRestoring Whether startup document restoration is still in progress.
 * @property persistenceErrorMessage Latest non-fatal persistence error shown by the UI.
 * @property isSaveDialogOpen Whether the active document promotion dialog is visible.
 */
data class ApiStudioState(
    val editorState: RequestEditorState = RequestEditorState(),
    val responseInspection: ResponseInspectorState? = null,
    val executionState: ExecutionState = ExecutionState.IDLE,
    val errorMessage: String? = null,
    val sessionContext: SessionContext = SessionContext.None,
    val activeDocumentTitle: String = "New Request",
    val activeDocumentNameOrigin: RequestNameOrigin = RequestNameOrigin.GENERATED,
    val selectedRequestId: String? = null,
    val isRestoring: Boolean = true,
    val persistenceErrorMessage: String? = null,
    val isSaveDialogOpen: Boolean = false
)
