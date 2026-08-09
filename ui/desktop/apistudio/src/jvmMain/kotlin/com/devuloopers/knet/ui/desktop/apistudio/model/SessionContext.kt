package com.devuloopers.knet.ui.desktop.apistudio.model

/**
 * Strongly-typed sealed interface representing the active editing session context in API Studio.
 *
 * Replaces the raw `activeSessionId: String` to make routing of field-change edits
 * explicit and type-safe at the ViewModel and UI layers.
 *
 * - [None]          — No session is active; the editor is blank.
 * - [UnsavedDraft]  — An unsaved scratch session is active; edits auto-save to the unsaved table.
 * - [SavedRequest]  — A saved collection request is active; edits auto-save in-place to Room DB.
 */
public sealed interface SessionContext {

    /**
     * No session is selected. The editor is in a blank / fresh state.
     */
    public data object None : SessionContext

    /**
     * An unsaved draft session is active.
     *
     * @property sessionId The unique ID of the unsaved draft (e.g. `"unsaved_1234567890"`).
     */
    public data class UnsavedDraft(val sessionId: String) : SessionContext

    /**
     * A saved collection request is currently open for editing.
     *
     * @property requestId The unique ID of the saved request record.
     * @property collectionId The ID of the parent API collection.
     * @property folderId The ID of the parent folder inside the collection.
     */
    public data class SavedRequest(
        val requestId: String,
        val collectionId: String,
        val folderId: String
    ) : SessionContext
}
