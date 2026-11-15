package com.devuloopers.knet.ui.desktop.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.usecase.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.OpaqueFlowSnapshot
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.application.contract.traffic.TrafficRecordId
import kotlinx.coroutines.flow.Flow
import com.devuloopers.knet.ui.desktop.traffic.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns selected-flow policy observation and in-place actions without coupling Traffic to another UI feature. */
class TrafficProtectionViewModel(
    private val observeHandling: ObserveProtectedTrafficFlowHandlingUseCase,
    private val setFlowAction: SetProtectedTrafficFlowActionUseCase,
    private val resetFlowAction: ResetProtectedTrafficFlowActionUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TrafficProtectionState())
    /** Live state for the currently selected opaque record. */
    val uiState = mutableState.asStateFlow()
    private var observation: Job? = null
    private var mutation: Job? = null

    private var selectionKey: SelectionKey? = null
    private var selectionRevision = 0L

    private data class SelectionKey(val flowId: OpaqueFlowId, val connectionId: com.devuloopers.knet.traffic.id.ConnectionId,
        val host: String, val port: Int?, val application: String?,
        val transport: com.devuloopers.knet.traffic.model.OpaqueTransportProtocol,
        val security: com.devuloopers.knet.traffic.model.OpaqueSecurityProtocol, val source: TrafficSource)

    private fun key(flow: OpaqueFlowSnapshot, source: TrafficSource) = SelectionKey(flow.id, flow.connectionId,
        flow.serverName ?: flow.destination.host, flow.destination.port, flow.sourceApplicationId, flow.transport, flow.security, source)

    /** Legacy/context-menu selection without an already loaded canonical row. */
    fun selectFlow(flowId: OpaqueFlowId?) {
        if (mutableState.value.flowId == flowId && selectionKey == null) return
        selectionKey = null
        observeSelection(flowId, null, flowId?.let { observeHandling(it) })
    }

    /** Selects canonical row metadata without discarding an immediately available current saved policy. */
    fun selectCapturedFlow(captured: OpaqueFlowSnapshot?, source: TrafficSource) {
        if (captured == null) { selectFlow(null); return }
        val nextKey = key(captured, source)
        if (selectionKey == nextKey) return
        selectionKey = nextKey
        observeSelection(captured.id, observeHandling.currentManualHandling(captured, source),
            observeHandling.observeSelection(captured, source))
    }

    /**
     * Pure first-frame projection: selection may render before its LaunchedEffect runs. Resolve current
     * manual policy immediately instead of mounting Loading/default buttons for that intermediate frame.
     * Mutation callbacks separately fence their expected flow ID; this presentation never authorizes IO.
     */
    fun stateForSelection(captured: OpaqueFlowSnapshot?, source: TrafficSource,
        observed: TrafficProtectionState = mutableState.value): TrafficProtectionState {
        if (captured == null) return observed
        if (observed.flowId == captured.id && selectionKey == key(captured, source)) return observed
        val preview = observeHandling.currentManualHandling(captured, source)
        return TrafficProtectionState(captured.id, preview, isLoading = preview == null, isApplying = observed.isApplying)
    }

    private fun observeSelection(flowId: OpaqueFlowId?, preview: ProtectedTrafficFlowHandling?,
        updates: Flow<ProtectedTrafficFlowHandling?>?) {
        observation?.cancel()
        val revision = ++selectionRevision
        mutableState.value = TrafficProtectionState(flowId = flowId, handling = preview,
            isLoading = flowId != null && preview == null, isApplying = mutation?.isActive == true)
        if (flowId == null || updates == null) return
        observation = viewModelScope.launch {
            try {
                updates.collect { handling ->
                    mutableState.update { state -> if (selectionRevision != revision || state.flowId != flowId) state else state.copy(
                        handling = handling, isLoading = handling != null && !handling.configurationReady,
                        feedback = if (handling == null) TrafficProtectionFeedback.Failed("Current handling is unavailable for this retained flow.") else state.feedback) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { state -> if (selectionRevision != revision || state.flowId != flowId) state else state.copy(handling = null, isLoading = false,
                    feedback = TrafficProtectionFeedback.Failed("Unable to load current connection handling.")) }
            }
        }
    }

    /** Saves a typed rule through the same application workflow as Traffic's context menu. */
    fun applyFlowAction(flowId: String, action: ProtectedTrafficAction, scope: ProtectedTrafficQuickRuleScope) {
        val state = mutableState.value
        if (scope == ProtectedTrafficQuickRuleScope.DESTINATION && state.flowId?.value == flowId &&
            state.destinationAction(action).status in setOf(TrafficProtectionActionStatus.ACTIVE, TrafficProtectionActionStatus.OVERRIDDEN)) return
        mutate(OpaqueFlowId(flowId), action) {
            when (setFlowAction.execute(OpaqueFlowId(flowId), action, scope)) {
                is SetProtectedTrafficFlowActionResult.Saved -> TrafficProtectionFeedback.Saved(action)
                SetProtectedTrafficFlowActionResult.DefaultRestored -> TrafficProtectionFeedback.DefaultRestored
                SetProtectedTrafficFlowActionResult.MissingFlow -> error("The selected protected flow no longer exists.")
                SetProtectedTrafficFlowActionResult.SourceApplicationUnavailable -> error("A verified source application is unavailable for this flow.")
                SetProtectedTrafficFlowActionResult.DestinationUnavailable -> error("The selected flow has no valid destination.")
            }
        }
    }

    /** Saves the original request's destination without treating an exchange ID as an opaque-flow ID. */
    fun applyRecordAction(record: TrafficRecordId, action: ProtectedTrafficAction, onFailure: (String) -> Unit) {
        if (record is TrafficRecordId.Opaque) {
            applyFlowAction(record.id.value, action, ProtectedTrafficQuickRuleScope.DESTINATION)
            return
        }
        if (mutation?.isActive == true) return
        mutation = viewModelScope.launch {
            mutableState.update { it.copy(isApplying = true) }
            try {
                when (setFlowAction.execute(record, action)) {
                    is SetProtectedTrafficFlowActionResult.Saved -> Unit
                    SetProtectedTrafficFlowActionResult.MissingFlow -> onFailure("The selected request no longer exists.")
                    else -> onFailure("The selected request has no valid destination.")
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                onFailure(failure.message ?: "Unable to update connection handling.")
            } finally { mutableState.update { it.copy(isApplying = false) } }
        }
    }

    /** Submits an inspector action only after authoritative policy is ready and the choice is meaningful. */
    fun applyDestinationAction(action: ProtectedTrafficAction, expectedFlowId: OpaqueFlowId? = mutableState.value.flowId) {
        val state = mutableState.value
        if (state.flowId != expectedFlowId) return
        val flowId = state.flowId ?: return
        if (!state.destinationAction(action).enabled) return
        applyFlowAction(flowId.value, action, ProtectedTrafficQuickRuleScope.DESTINATION)
    }

    /** Restores inherited policy without changing application/wildcard rules or captured history. */
    fun useDefaultHandling(expectedFlowId: OpaqueFlowId? = mutableState.value.flowId) {
        val state = mutableState.value
        if (state.flowId != expectedFlowId) return
        val flowId = state.flowId ?: return
        if (state.isLoading || state.isApplying || state.handling?.configurationReady != true) return
        mutate(flowId, null) {
            when (resetFlowAction.execute(flowId)) {
                SetProtectedTrafficFlowActionResult.DefaultRestored -> TrafficProtectionFeedback.DefaultRestored
                SetProtectedTrafficFlowActionResult.MissingFlow -> error("The selected protected flow no longer exists.")
                else -> error("The selected flow has no valid destination.")
            }
        }
    }

    private fun mutate(flowId: OpaqueFlowId, action: ProtectedTrafficAction?, block: suspend () -> TrafficProtectionFeedback) {
        if (mutation?.isActive == true) return
        mutableState.update { state -> state.copy(isApplying = true,
            applyingAction = action.takeIf { state.flowId == flowId }, feedback = null) }
        mutation = viewModelScope.launch {
            try {
                val result = block()
                mutableState.update { state -> if (state.flowId == flowId) state.copy(feedback = result) else state }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { state -> if (state.flowId == flowId) state.copy(feedback =
                    TrafficProtectionFeedback.Failed(failure.message ?: "Unable to update connection handling.")) else state }
            } finally { mutableState.update { it.copy(isApplying = false, applyingAction = null) } }
        }
    }
}
