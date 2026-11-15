package com.devuloopers.knet.ui.desktop.httppanel.copy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.application.usecase.traffic.PrepareStudioCopyUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Clipboard status never retains request or response payloads. */
data class StudioDetailsCopyState(
    /** Current command is preparing or publishing. */ val copying: Boolean = false,
    /** Incremented only after a successful clipboard write. */ val copiedRevision: Long = 0,
    /** Current owner's actionable error. */ val error: String? = null,
)

/** Execution/command ownership fencing for all HTTP and native API Studio copy controls. */
class StudioDetailsCopyViewModel(private val prepare: PrepareStudioCopyUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow(StudioDetailsCopyState())
    /** Lightweight UI status and feedback. */ val state: StateFlow<StudioDetailsCopyState> = mutableState.asStateFlow()
    private var owner: String? = null
    private var revision = 0L
    private var task: Job? = null
    /** Cancels obsolete work and drops errors/feedback when execution, document, clear or editor changes. */
    fun selectOwner(id: String?) { if (owner != id) { owner = id; revision++; task?.cancel(); mutableState.value = StudioDetailsCopyState() } }
    /** Freezes source and preset synchronously; no draft state can be read after this call. */
    fun copy(id: String, preset: TrafficCopyPreset, clipboard: suspend (String) -> Unit) {
        if (owner != id) return
        val expected = ++revision; task?.cancel(); mutableState.update { it.copy(copying = true, error = null) }
        task = viewModelScope.launch {
            try {
                val text = prepare.execute(id, preset)
                ensureActive(); if (expected != revision || owner != id) return@launch
                clipboard(text)
                if (expected == revision) mutableState.update { it.copy(copying = false, copiedRevision = it.copiedRevision + 1) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (expected == revision) mutableState.update { it.copy(copying = false, error = failure.message ?: "Unable to copy execution details.") } }
        }
    }
}
