package com.devuloopers.knet.ui.desktop.app.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.device.DeviceWorkspaceEntry
import com.devuloopers.knet.application.contract.device.DevicePresenceStatus
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import com.devuloopers.knet.application.usecase.device.*
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputHistory
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputSampler
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Lightweight shell state; device tabs never retain captured rows or payload editors.
 * @property devices Stable retained identity roster with current status.
 * @property source Selected Traffic source; other destinations keep global behavior.
 * @property throughput Current source's bounded, independently sampled rate history.
 * @property clearConfirmation Explicit offline device target awaiting confirmation, independent of selection.
 * @property pendingCounts Pending interception counts projected onto the workspace tabs.
 * @property closeError Recoverable Close failure presented even when Traffic is hidden.
 * @property closingWorkspace Confirmed target retained until deletion completes, including observed reconnects.
 */
data class DeviceWorkspaceState(val devices: List<DeviceWorkspaceEntry> = emptyList(),
    val source: TrafficSource = TrafficSource.Other, val throughput: NetworkThroughputHistory = NetworkThroughputHistory(),
    val clearConfirmation: DeviceTrafficClearConfirmation? = null,
    val pendingCounts: Map<com.devuloopers.knet.domain.policy.PolicyWorkspace, Int> = emptyMap(),
    val closeError: String? = null,
    val closingWorkspace: DeviceTrafficClearConfirmation? = null)

/**
 * Snapshot of a user-requested destructive action; switching tabs never retargets this confirmation.
 * @property source Exact verified identity whose entire locally retained traffic will be removed.
 * @property deviceLabel Device name and distinct identity suffix shown when confirmation was opened.
 * @property hasReconnected A connection observed at any point during confirmation or deletion keeps the tab open.
 */
data class DeviceTrafficClearConfirmation(val source: TrafficSource.Device, val deviceLabel: String,
    val hasReconnected: Boolean = false)

/** Coordinates stable roster/selection and the existing rate sampler through application use cases only. */
class DeviceWorkspaceViewModel(
    observeWorkspaces: ObserveDeviceWorkspacesUseCase,
    observeSelection: ObserveDeviceWorkspaceSelectionUseCase,
    private val selectWorkspace: SelectDeviceWorkspaceUseCase,
    private val observeTraffic: ObserveSourceTrafficUseCase,
    private val setWorkspaceOpen: SetDeviceWorkspaceOpenUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeviceWorkspaceState())
    /** Immutable presentation snapshot; credentials and socket objects never enter this state. */
    val state: StateFlow<DeviceWorkspaceState> = mutableState.asStateFlow()
    private val emptyThroughput = NetworkThroughputHistory()
    /** Stable navigation projection: sampling rate updates must not recompose the window or resolve every feature VM. */
    val navigationState: StateFlow<DeviceWorkspaceState> = com.devuloopers.knet.application.contract.device.ProjectedStateFlow(state) {
        it.copy(throughput = emptyThroughput)
    }
    private var selectionInitialized = false
    private var monitoringJob: Job? = null

    init {
        viewModelScope.launch {
            val restored = try { observeSelection.execute().first() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { TrafficSource.Other }
            observeWorkspaces.execute().collect { devices ->
                val wanted = if (!selectionInitialized) restored else state.value.source
                selectionInitialized = true
                val valid = wanted !is TrafficSource.Device || devices.any { entry -> entry.device.id == wanted.id }
                val selected = if (valid && wanted != TrafficSource.All) wanted else TrafficSource.Other
                mutableState.update { current ->
                    fun trackReconnect(action: DeviceTrafficClearConfirmation?): DeviceTrafficClearConfirmation? =
                        action?.let { pending ->
                            if (devices.any { it.device.id == pending.source.id && it.status != DevicePresenceStatus.OFFLINE })
                                pending.copy(hasReconnected = true) else pending
                        }
                    current.copy(devices = devices, source = selected,
                        clearConfirmation = trackReconnect(current.clearConfirmation),
                        closingWorkspace = trackReconnect(current.closingWorkspace),
                        throughput = if (current.source == selected) current.throughput else NetworkThroughputHistory())
                }
                // A disappearing selected tab falls back once; reconnecting it must not steal focus later.
                if (selected != wanted) persistSelection(selected)
            }
        }
    }

    /** Changes a view only; forwarding, policy rules and capture admission are never changed here. */
    fun select(source: TrafficSource) {
        require(source != TrafficSource.All)
        if (source is TrafficSource.Device && state.value.devices.none { entry -> entry.device.id == source.id }) return
        selectionInitialized = true
        mutableState.update { current -> current.copy(source = source, throughput = NetworkThroughputHistory()) }
        persistSelection(source)
    }

    /**
     * Routes toolbar Clear independently of presence, roster timing or a pending Close. Traffic owns the
     * duplicate-operation guard; a workspace shell operation must never silently block traffic deletion.
     */
    fun requestClear(source: TrafficSource): TrafficSource? {
        return source.takeUnless { it == TrafficSource.All }
    }

    /** Opens confirmation for the clicked offline tab without changing the selected Traffic workspace. */
    fun requestClose(id: RegisteredDeviceId) {
        if (state.value.clearConfirmation != null || state.value.closingWorkspace != null) return
        val entry = state.value.devices.find { device -> device.device.id == id } ?: return
        if (entry.status != DevicePresenceStatus.OFFLINE) return
        mutableState.update { current -> current.copy(clearConfirmation =
            DeviceTrafficClearConfirmation(TrafficSource.Device(id), deviceWorkspaceLabels(current.devices).getValue(id))) }
    }

    /** Dismisses without changing selection, history, forwarding or pairing. */
    fun dismissClear() { mutableState.update { current -> current.copy(clearConfirmation = null) } }

    /**
     * Consumes confirmation once and returns its original source, even if the device reconnected.
     */
    fun confirmClear(): TrafficSource.Device? {
        val pending = state.value.clearConfirmation ?: return null
        mutableState.update { it.copy(clearConfirmation = null, closingWorkspace = pending) }
        return pending.source
    }

    /** Retain an existing device tab before deleting the history that may currently keep it visible. */
    suspend fun retainForClear(source: TrafficSource) {
        if (source is TrafficSource.Device) setWorkspaceOpen.execute(source.id, true)
    }

    /** Complete Close only after deletion; a reconnect always keeps the tab open. */
    suspend fun finishClose(source: TrafficSource.Device) {
        if (state.value.closingWorkspace?.source != source) return
        fun keepOpen(): Boolean = state.value.closingWorkspace?.hasReconnected == true ||
            state.value.devices.any { it.device.id == source.id && it.status != DevicePresenceStatus.OFFLINE }
        try {
            if (keepOpen()) return
            setWorkspaceOpen.execute(source.id, false)
            // A preference write suspends. Retain a reconnect observed while that write was committing too.
            if (keepOpen()) setWorkspaceOpen.execute(source.id, true)
        } finally {
            mutableState.update { it.copy(closingWorkspace = null) }
        }
    }

    /** Releases a failed Close for retry and keeps its workspace visible with an actionable error. */
    fun closeFailed() { mutableState.update { it.copy(closingWorkspace = null,
        closeError = "The device workspace could not be closed. Its traffic may not have been fully cleared; please try again.") } }
    /** Acknowledges the previous Close failure without changing traffic, pairing or tab retention. */
    fun dismissCloseError() { mutableState.update { it.copy(closeError = null) } }

    private fun persistSelection(source: TrafficSource) {
        viewModelScope.launch {
            try { selectWorkspace.execute(source) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The active selection remains usable if preference persistence is unavailable. */ }
        }
    }

    /** Owns one sample subscriber only while Traffic is visible; hidden tabs require no telemetry jobs. */
    fun setMonitoring(active: Boolean) {
        if (!active) { monitoringJob?.cancel(); monitoringJob = null; return }
        if (monitoringJob != null) return
        monitoringJob = viewModelScope.launch {
            state.map { current -> current.source }.distinctUntilChanged().collectLatest { source ->
                val sampler = NetworkThroughputSampler()
                mutableState.update { current -> current.copy(throughput = NetworkThroughputHistory()) }
                observeTraffic.execute(source).collect { snapshot ->
                    val history = sampler.accept(NetworkConditionRuntimeSnapshot(sampledAtNanos = snapshot.sampledAtNanos,
                        uploadedBytes = snapshot.uploadedBytes, downloadedBytes = snapshot.downloadedBytes))
                    if (history != null) mutableState.update { current -> if (current.source == source) current.copy(throughput = history) else current }
                }
            }
        }
    }
}
