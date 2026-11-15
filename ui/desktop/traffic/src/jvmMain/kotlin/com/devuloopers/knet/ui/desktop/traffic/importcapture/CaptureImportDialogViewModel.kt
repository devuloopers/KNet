package com.devuloopers.knet.ui.desktop.traffic.importcapture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.importcapture.*
import com.devuloopers.knet.application.usecase.traffic.*
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.core.logger.KNetLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** One app-wide file intake session, independent of captured Traffic. */
data class CaptureImportDialogState(
    /** Whether the shared chooser is open over the current workspace. */
    val visible: Boolean = false,
    /** Whether a native file drag is currently over KNet before release. */
    val dragging: Boolean = false,
    /** Whether Escape cancelled feedback for the current native drag. */
    val dragSuppressed: Boolean = false,
    /** Validated private handle owned by this chooser until explicit handoff. */
    val capture: ImportedCapture? = null,
    /** At most 128 body-free summaries from the current file page. */
    val rows: List<ImportedRecord> = emptyList(),
    /** Selected full record used by draft actions; null during a detail read. */
    val selected: ImportedRecord? = null,
    /** Zero-based starting record index of the displayed page. */
    val offset: Long = 0,
    /** Whether validation, paging or action preparation is running. */
    val loading: Boolean = false,
    /** Bounded parser progress while validating a local capture. */
    val progress: CaptureImportProgress? = null,
    /** Retryable presentation message without discarding a prior valid candidate. */
    val error: String? = null,
    /** Action currently being converted into an immutable editor handoff. */
    val preparingAction: CaptureImportAction? = null,
    /** Chosen by the app, never copied from the file's recorded device identity. */
    val workspace: PolicyWorkspace = PolicyWorkspace.Other,
    /** Current app-provided display name for the captured policy workspace. */
    val workspaceName: String = "Other traffic",
) {
    /** One continuous presentation from native entry through accepted handoff and validation. */
    val presented: Boolean get() = visible || (dragging && !dragSuppressed)
}

/** Opaque single-use reservation; late native delivery cannot reopen or replace a newer import. */
class CaptureImportDropToken internal constructor()

/**
 * Shared picker/drop orchestration. This overlay owns every temporary spool until dismissal,
 * replacement or a successful editor handoff. Generations fence every late native and parser result.
 */
class CaptureImportDialogViewModel(
    private val openCapture: OpenCaptureImportUseCase,
    private val readPage: ReadCaptureImportPageUseCase,
    private val readRecord: ReadCaptureImportRecordUseCase,
    private val prepareAction: PrepareCaptureImportActionUseCase,
    private val closeCapture: CloseCaptureImportUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CaptureImportDialogState())
    /** Immutable observations shared by every app destination. */
    val state: StateFlow<CaptureImportDialogState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var selection: Job? = null
    private var generation = 0L
    private var selectionRevision = 0L
    private var pendingDrop: CaptureImportDropToken? = null

    /** Opens the chooser without parsing, picking a file, or leaving the current screen. */
    fun show(workspace: PolicyWorkspace, name: String) {
        if (state.value.visible) return
        mutableState.value = CaptureImportDialogState(visible = true, workspace = workspace, workspaceName = name)
    }

    /** Shows feedback immediately on native drag entry; exit/end do not dismiss an already opened chooser. */
    fun dragging(active: Boolean) {
        mutableState.update { if (active && (it.loading || it.dragSuppressed)) it
            else it.copy(dragging = active, dragSuppressed = if (active) it.dragSuppressed else false) }
    }

    /**
     * Reserves the existing surface synchronously at release, before AWT completes the drag.
     * No file access occurs here; native path delivery is deferred and must present this reservation.
     */
    fun beginDrop(workspace: PolicyWorkspace, name: String): CaptureImportDropToken? {
        if (state.value.loading || state.value.dragSuppressed) return null
        if (!state.value.visible) show(workspace, name)
        ++generation; ++selectionRevision; selection?.cancel()
        val token = CaptureImportDropToken()
        pendingDrop = token
        mutableState.update { it.copy(visible = true, dragging = false, loading = true, error = null, progress = null, preparingAction = null) }
        return token
    }

    /** Accepts one file for the reserved drop; cancelled or superseded native deliveries are ignored. */
    fun completeDrop(token: CaptureImportDropToken, paths: List<String>) {
        if (pendingDrop !== token) return
        pendingDrop = null
        mutableState.update { it.copy(loading = false) }
        if (paths.size != 1) { mutableState.update { it.copy(error = "Drop one capture file at a time.") }; return }
        load { progress -> openCapture.execute(paths.single(), progress) }
    }

    /** Shows transfer errors in the reserved surface, preserving any previous valid candidate. */
    fun rejectDrop(token: CaptureImportDropToken, message: String) {
        if (pendingDrop !== token) return
        pendingDrop = null
        mutableState.update { it.copy(loading = false, error = message) }
    }

    /** Chooses a file from the dialog; cancelling keeps both the chooser and any prior validated file. */
    fun chooseFile() {
        if (!state.value.visible || state.value.loading) return
        load { progress -> openCapture.execute(progress) }
    }

    private fun load(open: suspend ((CaptureImportProgress) -> Unit) -> ImportedCapture?) {
        if (state.value.loading) return
        val token = ++generation
        selection?.cancel(); ++selectionRevision
        mutableState.update { it.copy(loading = true, error = null, progress = null, preparingAction = null) }
        operation = viewModelScope.launch {
            var prepared: ImportedCapture? = null
            var installed = false
            try {
                prepared = open { progress -> mutableState.update { if (generation == token) it.copy(progress = progress) else it } }
                val capture = prepared ?: run { if (generation == token) mutableState.update { it.copy(loading = false) }; return@launch }
                val rows = readPage.execute(capture.id, 0)
                val record = rows.firstOrNull()?.let { readRecord.execute(capture.id, it.index) }
                ensureActive(); if (generation != token) return@launch
                val previous = state.value.capture
                mutableState.update { it.copy(capture = capture, rows = rows, selected = record, offset = 0, loading = false, progress = null) }
                installed = true
                if (previous?.id != capture.id) previous?.let { retire(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == token) mutableState.update { it.copy(loading = false, error = failure.message ?: "Unable to import this file.") } }
            finally { if (!installed) prepared?.let { withContext(NonCancellable) { release(it) } } }
        }
    }

    /** Pages only the validated file's bounded summaries for record-specific actions. */
    fun page(offset: Long) {
        val capture = state.value.capture ?: return
        if (state.value.loading || offset !in 0 until capture.records) return
        val token = ++generation; selection?.cancel(); ++selectionRevision
        mutableState.update { it.copy(loading = true, error = null) }
        operation = viewModelScope.launch {
            try {
                val rows = readPage.execute(capture.id, offset)
                val record = rows.firstOrNull()?.let { readRecord.execute(capture.id, it.index) }
                ensureActive()
                if (generation == token) mutableState.update { it.copy(rows = rows, selected = record, offset = offset, loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == token) mutableState.update { it.copy(loading = false, error = failure.message ?: "Unable to read this capture page.") } }
        }
    }

    /** Changes the selected file-local record without allowing a late read to replace a newer choice. */
    fun select(index: Long) {
        val capture = state.value.capture ?: return
        if (state.value.loading || state.value.rows.none { it.index == index }) return
        selection?.cancel(); val revision = ++selectionRevision; val token = generation
        mutableState.update { it.copy(selected = null, error = null) }
        selection = viewModelScope.launch {
            try {
                val record = readRecord.execute(capture.id, index)
                ensureActive()
                if (generation == token && selectionRevision == revision) mutableState.update { it.copy(selected = record) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == token && selectionRevision == revision) mutableState.update { it.copy(error = failure.message ?: "Unable to read this record.") } }
        }
    }

    /** Explains eligibility using validated metadata; parsing or detail loads keep all actions disabled. */
    fun unavailableReason(action: CaptureImportAction): String? = when {
        state.value.loading -> "Wait for file validation to finish."
        state.value.capture == null -> "Choose or drop a capture file first."
        state.value.capture?.records == 0L -> "This file contains no records. Choose another file."
        else -> prepareAction.unavailableReason(action, state.value.selected)
    }

    /** Produces one immutable editor handoff. No request or policy is executed by this operation. */
    fun perform(action: CaptureImportAction, onReady: (CaptureImportActionDraft) -> Unit) {
        val current = state.value
        val capture = current.capture ?: return
        if (unavailableReason(action) != null || current.loading) return
        val token = ++generation; selection?.cancel(); ++selectionRevision
        mutableState.update { it.copy(loading = true, error = null, preparingAction = action) }
        operation = viewModelScope.launch {
            try {
                val draft = prepareAction.execute(capture, current.selected?.index, action, current.workspace)
                ensureActive(); if (generation != token) return@launch
                // Handoff is synchronous: no cancellation point can separate ownership transfer from publication.
                onReady(draft)
                mutableState.value = CaptureImportDialogState()
                retire(capture)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == token) mutableState.update { it.copy(loading = false, preparingAction = null, error = failure.message ?: "Unable to prepare this action.") } }
        }
    }

    /** Cancels this chooser and removes its private candidate, preserving captured traffic. */
    fun dismiss() {
        ++generation; ++selectionRevision; operation?.cancel(); selection?.cancel()
        pendingDrop = null
        val capture = state.value.capture
        mutableState.value = CaptureImportDialogState(dragSuppressed = state.value.dragging)
        capture?.let { retire(it) }
    }

    private fun retire(capture: ImportedCapture) {
        // Enter protected cleanup before a simultaneous owner clear can cancel the launch itself.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { release(capture) } }
    }

    private suspend fun release(capture: ImportedCapture) {
        try { closeCapture.execute(capture.id) }
        catch (failure: Exception) { KNetLogger.error("CaptureImportDialog", failure) { "Unable to retire a private import spool." } }
    }

    override fun onCleared() {
        pendingDrop = null; ++generation; ++selectionRevision
        val capture = state.value.capture
        mutableState.value = CaptureImportDialogState()
        if (capture != null) CoroutineScope(Dispatchers.IO).launch { try { release(capture) } finally { cancel() } }
        super.onCleared()
    }
}
