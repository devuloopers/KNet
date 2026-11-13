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
 */
data class DeviceWorkspaceState(val devices: List<DeviceWorkspaceEntry> = emptyList(),
    val source: TrafficSource = TrafficSource.Other, val throughput: NetworkThroughputHistory = NetworkThroughputHistory(),
    val clearConfirmation: DeviceTrafficClearConfirmation? = null)

/**
 * Snapshot of a user-requested destructive action; switching tabs never retargets this confirmation.
 * @property source Exact verified identity whose entire locally retained traffic will be removed.
 * @property deviceLabel Device name and distinct identity suffix shown when confirmation was opened.
 */
data class DeviceTrafficClearConfirmation(val source: TrafficSource.Device, val deviceLabel: String)

/** Coordinates stable roster/selection and the existing rate sampler through application use cases only. */
class DeviceWorkspaceViewModel(
    observeWorkspaces: ObserveDeviceWorkspacesUseCase,
    observeSelection: ObserveDeviceWorkspaceSelectionUseCase,
    private val selectWorkspace: SelectDeviceWorkspaceUseCase,
    private val observeTraffic: ObserveSourceTrafficUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeviceWorkspaceState())
    /** Immutable presentation snapshot; credentials and socket objects never enter this state. */
    val state: StateFlow<DeviceWorkspaceState> = mutableState.asStateFlow()
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
                    current.copy(devices = devices, source = selected,
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
     * Routes toolbar Clear: connected/grace-period devices and Other clear immediately; offline devices
     * use the same confirmation as tab Close. Returns only an immediately authorized source to clear.
     */
    fun requestClear(source: TrafficSource): TrafficSource? {
        if (source == TrafficSource.All || state.value.clearConfirmation != null) return null
        if (source is TrafficSource.Device) {
            val entry = state.value.devices.find { device -> device.device.id == source.id } ?: return null
            if (entry.status == DevicePresenceStatus.OFFLINE) {
                requestClose(source.id)
                return null
            }
        }
        return source
    }

    /** Opens confirmation for the clicked offline tab without changing the selected Traffic workspace. */
    fun requestClose(id: RegisteredDeviceId) {
        if (state.value.clearConfirmation != null) return
        val entry = state.value.devices.find { device -> device.device.id == id } ?: return
        if (entry.status != DevicePresenceStatus.OFFLINE) return
        mutableState.update { current -> current.copy(clearConfirmation =
            DeviceTrafficClearConfirmation(TrafficSource.Device(id), deviceWorkspaceLabels(current.devices).getValue(id))) }
    }

    /** Dismisses without changing selection, history, forwarding or pairing. */
    fun dismissClear() { mutableState.update { current -> current.copy(clearConfirmation = null) } }

    /**
     * Consumes confirmation once and returns its original source, even if the device reconnected.
     * Roster observation determines whether the tab remains after deletion; this never forces it hidden.
     */
    fun confirmClear(): TrafficSource.Device? {
        val pending = state.value.clearConfirmation ?: return null
        dismissClear()
        return pending.source
    }

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
