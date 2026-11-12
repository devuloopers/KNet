package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.application.usecase.protectedtraffic.ProtectedTrafficFlowHandling
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.traffic.id.OpaqueFlowId

/** Result shown beside the selected flow, never written back into historical capture metadata. */
sealed interface TrafficProtectionFeedback {
    /** A future-flow rule has been saved. */
    data class Saved(val action: ProtectedTrafficAction) : TrafficProtectionFeedback
    /** The destination override was removed and inherited policy restored. */
    data object DefaultRestored : TrafficProtectionFeedback
    /** A persistence or missing-history problem prevented the action. */
    data class Failed(val message: String) : TrafficProtectionFeedback
}

/** Independent policy presentation for one selected opaque record. */
data class TrafficProtectionState(
    /** Selection identity fences asynchronous updates and feedback. */
    val flowId: OpaqueFlowId? = null,
    /** Current saved/default rule, independent of captured evidence. */
    val handling: ProtectedTrafficFlowHandling? = null,
    /** Selected context is being resolved. */
    val isLoading: Boolean = false,
    /** A serialized persistence action is running. */
    val isApplying: Boolean = false,
    /** Selected-flow action currently being saved; null for a save on another selection. */
    val applyingAction: ProtectedTrafficAction? = null,
    /** Inline feedback belonging to this selection only. */
    val feedback: TrafficProtectionFeedback? = null,
)

/** Availability and persisted selection of a future-connection action. */
enum class TrafficProtectionActionStatus {
    /** This meaningful alternative can create or update an exact destination rule. */
    AVAILABLE,
    /** The enabled exact saved rule currently supplies the effective action. */
    ACTIVE,
    /** Persistence or authoritative publication for this action is pending. */
    SAVING,
    /** Loading or another in-flight mutation prevents submitting the action. */
    UNAVAILABLE,
    /** The exact saved choice exists, but another rule currently wins. */
    OVERRIDDEN,
}

/** Policy-derived action presentation; it never uses transient success feedback as persisted state. */
data class TrafficProtectionActionState(
    /** Operation offered by this control. */
    val action: ProtectedTrafficAction,
    /** Current persisted selection or availability. */
    val status: TrafficProtectionActionStatus,
) {
    /** Whether this exact saved rule is enabled and currently wins. */
    val selected: Boolean get() = status == TrafficProtectionActionStatus.ACTIVE
    /** Only a meaningful alternative may submit another mutation. */
    val enabled: Boolean get() = status == TrafficProtectionActionStatus.AVAILABLE
}

/**
 * Derives one action from authoritative rule identity and precedence, without retaining a last-click cache.
 * @param action Future-connection handling offered by the control.
 * @return Selection and availability derived from committed policy and the current mutation lifecycle.
 */
fun TrafficProtectionState.destinationAction(action: ProtectedTrafficAction): TrafficProtectionActionState {
    val current = handling?.takeIf { it.flowId == flowId }
    val status = when {
        isLoading || current?.configurationReady != true -> TrafficProtectionActionStatus.UNAVAILABLE
        else -> {
            val rule = current.destinationRule?.takeIf { it.enabled && it.action == action }
            val winningId = (current.decision.evidence as? com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficDecisionEvidence.Rule)?.ruleId
            when {
                rule != null && rule.id == winningId && current.decision.action == action -> TrafficProtectionActionStatus.ACTIVE
                isApplying && applyingAction == action -> TrafficProtectionActionStatus.SAVING
                rule != null && rule.id != winningId -> TrafficProtectionActionStatus.OVERRIDDEN
                isApplying -> TrafficProtectionActionStatus.UNAVAILABLE
                else -> TrafficProtectionActionStatus.AVAILABLE
            }
        }
    }
    return TrafficProtectionActionState(action, status)
}

/** Inspector callbacks; composables do not own policy mutation or navigation. */
data class TrafficProtectionActions(
    /** Saves exact-destination handling for future connections. */
    val apply: (ProtectedTrafficAction) -> Unit,
    /** Removes this destination override; null when the host does not provide reset controls. */
    val useDefault: (() -> Unit)? = null,
)
