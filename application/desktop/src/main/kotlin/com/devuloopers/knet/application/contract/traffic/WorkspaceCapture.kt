package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.domain.policy.PolicyWorkspace

/**
 * Immutable recording admission, selected using authenticated ingress rather than the active UI tab.
 * @property defaultEnabled Admission for identities without an explicit process-session choice.
 * @property overrides Independent choices keyed by complete registered identity or Other traffic.
 */
public data class CaptureAdmissionPolicy(
    public val defaultEnabled: Boolean = true,
    public val overrides: Map<PolicyWorkspace, Boolean> = emptyMap(),
) {
    /** Returns whether [workspace] may start a new complete capture record. */
    public fun enabledOn(workspace: PolicyWorkspace): Boolean = overrides[workspace] ?: defaultEnabled
}

/**
 * One coherent view of the shared writer and independent source recording choices.
 * @property sessionState Physical writer lifecycle; source choices never stop the proxy listener.
 * @property admission Recording choices retained across tab switches, reconnects and writer rotation.
 */
public data class WorkspaceCaptureSnapshot(
    public val sessionState: CaptureSessionState = CaptureSessionState.Inactive,
    public val admission: CaptureAdmissionPolicy = CaptureAdmissionPolicy(),
) {
    /** Projects a source's toolbar state without allocating a coroutine or a second writer. */
    public fun stateFor(workspace: PolicyWorkspace): CaptureSessionState = when (sessionState) {
        is CaptureSessionState.Capturing, CaptureSessionState.Starting ->
            if (admission.enabledOn(workspace)) sessionState else CaptureSessionState.Paused
        else -> sessionState
    }
}

/** Maps an explicit Traffic tab to the same immutable identity used by runtime policy admission. */
public fun TrafficSource.captureWorkspace(): PolicyWorkspace = when (this) {
    is TrafficSource.Device -> PolicyWorkspace.Device(id)
    TrafficSource.Other, TrafficSource.All -> PolicyWorkspace.Other
}
