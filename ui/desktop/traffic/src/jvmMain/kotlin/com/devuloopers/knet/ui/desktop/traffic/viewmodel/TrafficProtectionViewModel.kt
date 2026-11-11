package com.devuloopers.knet.ui.desktop.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.usecase.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.traffic.id.OpaqueFlowId
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
) : ViewModel() {
    private val mutableState = MutableStateFlow(TrafficProtectionState())
    /** Live state for the currently selected opaque record. */
    val uiState = mutableState.asStateFlow()
    private var observation: Job? = null
    private var mutation: Job? = null

    /** Replaces the observed context; late results from another selection are ignored. */
    fun selectFlow(flowId: OpaqueFlowId?) {
        if (mutableState.value.flowId == flowId) return
        observation?.cancel()
        mutableState.value = TrafficProtectionState(flowId = flowId, isLoading = flowId != null, isApplying = mutation?.isActive == true)
        if (flowId == null) return
        observation = viewModelScope.launch {
            try {
                observeHandling(flowId).collect { handling ->
                    mutableState.update { state -> if (state.flowId != flowId) state else state.copy(
                        handling = handling, isLoading = false,
                        feedback = if (handling == null) TrafficProtectionFeedback.Failed("Current handling is unavailable for this retained flow.") else state.feedback) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { state -> if (state.flowId != flowId) state else state.copy(isLoading = false,
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
                SetProtectedTrafficFlowActionResult.MissingFlow -> error("The selected protected flow no longer exists.")
                SetProtectedTrafficFlowActionResult.SourceApplicationUnavailable -> error("A verified source application is unavailable for this flow.")
                SetProtectedTrafficFlowActionResult.DestinationUnavailable -> error("The selected flow has no valid destination.")
            }
        }
    }

    /** Submits an inspector action only after authoritative policy is ready and the choice is meaningful. */
    fun applyDestinationAction(action: ProtectedTrafficAction) {
        val state = mutableState.value
        val flowId = state.flowId ?: return
        if (!state.destinationAction(action).enabled) return
        applyFlowAction(flowId.value, action, ProtectedTrafficQuickRuleScope.DESTINATION)
    }

    private fun mutate(flowId: OpaqueFlowId, action: ProtectedTrafficAction, block: suspend () -> TrafficProtectionFeedback) {
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
