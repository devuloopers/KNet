package com.devuloopers.knet.ui.desktop.traffic.copy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.export.CaptureExportSource
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.application.usecase.traffic.PrepareTrafficDetailsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Only command status is retained; copied payload text never becomes long-lived presentation state. */
data class TrafficDetailsCopyState(
    /** Whether the current source's explicit copy is resolving records or publishing its clipboard entry. */
    val copying: Boolean = false,
    /** Actionable failure for the active source; the previous clipboard is preserved on preparation failure. */
    val error: String? = null,
)

/** Latest-command ownership and source/owner fencing around the asynchronous Compose clipboard callback. */
class TrafficDetailsCopyViewModel(private val copyDetails: PrepareTrafficDetailsUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow(TrafficDetailsCopyState())
    /** Lightweight copy status for the existing Traffic error banner. */
    val state: StateFlow<TrafficDetailsCopyState> = mutableState.asStateFlow()
    private var task: Job? = null
    private var revision = 0L
    private var source: TrafficSource? = null

    /** Cancels publication when the user leaves the invoking device/source. */
    fun selectSource(value: TrafficSource) {
        if (source == value) return
        source = value; ++revision; task?.cancel(); mutableState.value = TrafficDetailsCopyState()
    }

    /** Freezes the request before suspension; only the current command may publish through [writeClipboard]. */
    fun copy(request: CaptureExportSource.Selection, preset: TrafficCopyPreset = TrafficCopyPreset.FULL, writeClipboard: suspend (String) -> Unit) {
        if (source != null && source != request.source) return
        val frozen = request.copy(records = request.records.toList())
        val token = ++revision; task?.cancel()
        mutableState.value = TrafficDetailsCopyState(copying = true)
        task = viewModelScope.launch {
            try {
                val text = copyDetails.execute(frozen, preset)
                ensureActive(); if (revision != token) return@launch
                writeClipboard(text)
                if (revision == token) mutableState.value = TrafficDetailsCopyState()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (revision == token) mutableState.value = TrafficDetailsCopyState(error = failure.message ?: "Unable to copy traffic details.") }
        }
    }

    /** Dismisses only copy-specific feedback. */
    fun clearError() { mutableState.update { it.copy(error = null) } }
}
