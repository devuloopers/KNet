package com.devuloopers.knet.ui.desktop.httppanel.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.application.usecase.traffic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Immutable dialog state; a frozen source stays owned by the original invocation across tab changes. */
data class CaptureExportState(
    val label: String = "",
    val source: CaptureExportSource? = null,
    val scopes: List<Pair<String, CaptureExportSource>> = emptyList(),
    val prepared: PreparedCaptureExport? = null,
    val options: CaptureExportOptions = CaptureExportOptions(),
    val preparing: Boolean = false,
    val saving: Boolean = false,
    val progress: CaptureExportProgress? = null,
    val error: String? = null,
    val complete: Boolean = false,
)

/** UI-independent snapshot/save lifecycle with cancellation and late-completion fencing. */
class CaptureExportViewModel(
    private val prepare: PrepareCaptureExportUseCase,
    private val save: SaveCaptureExportUseCase,
    private val release: ReleaseCaptureExportUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CaptureExportState())
    /** Current frozen-source dialog, including exact HAR loss counts. */
    val state: StateFlow<CaptureExportState> = mutableState.asStateFlow()
    private var revision = 0L
    private var task: Job? = null

    /** Immediately captures the source, then prepares an isolated bounded-memory spool in the background. */
    fun open(source: CaptureExportSource, label: String, scopes: List<Pair<String, CaptureExportSource>> = emptyList()) {
        dismiss()
        val expected = ++revision
        val choices = if (scopes.isNotEmpty()) scopes else (source as? CaptureExportSource.Traffic)?.let { traffic ->
            val all = com.devuloopers.knet.application.contract.traffic.TrafficPageQuery(source = traffic.query.source, limit = 128)
            listOf("Filtered results" to source, "All traffic for this source" to CaptureExportSource.Traffic(all),
                "All sources" to CaptureExportSource.Traffic(all.copy(source = com.devuloopers.knet.application.contract.traffic.TrafficSource.All)))
        }.orEmpty()
        mutableState.value = CaptureExportState(source = source, scopes = choices, label = label, preparing = true)
        task = viewModelScope.launch {
            var retained: PreparedCaptureExport? = null
            try {
                retained = prepare.execute(source)
                if (revision != expected) return@launch
                mutableState.update { it.copy(prepared = retained, preparing = false) }
                retained = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (revision == expected) mutableState.update { it.copy(preparing = false, error = failure.message ?: "Unable to prepare export.") } }
            finally { retained?.let { withContext(NonCancellable) { release.execute(it) } } }
        }
    }

    /** Changes only serialization/privacy choices; source membership and payloads remain frozen. */
    fun options(value: CaptureExportOptions) { if (!state.value.saving) mutableState.update { it.copy(options = value) } }

    /** Uses the native save dialog and atomically publishes the chosen format. */
    fun save() {
        val snapshot = state.value
        val prepared = snapshot.prepared ?: return
        if (snapshot.saving || snapshot.complete) return
        val expected = revision
        mutableState.update { it.copy(saving = true, error = null) }
        task = viewModelScope.launch {
            try {
                val completed = save.execute(prepared, snapshot.options) { progress ->
                    if (revision == expected) mutableState.update { it.copy(progress = progress) }
                }
                if (revision == expected) mutableState.update { it.copy(saving = false, complete = completed) }
                if (completed) { withContext(NonCancellable) { release.execute(prepared) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (revision == expected) mutableState.update { it.copy(saving = false, error = failure.message ?: "Unable to save export.") } }
        }
    }

    /** Cancels the current task; cleanup waits for that writer before retiring its spool. */
    fun dismiss() {
        revision++
        val previous = task
        previous?.cancel()
        val prepared = state.value.prepared
        mutableState.value = CaptureExportState()
        if (prepared != null) CoroutineScope(Dispatchers.IO).launch { previous?.join(); release.execute(prepared) }
    }
    override fun onCleared() { dismiss(); super.onCleared() }
}
