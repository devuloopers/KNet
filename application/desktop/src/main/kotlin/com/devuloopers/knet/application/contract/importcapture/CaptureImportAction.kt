package com.devuloopers.knet.application.contract.importcapture

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.domain.rules.model.BreakpointRule

/** Explicit destinations offered only after local capture validation. */
public enum class CaptureImportAction {
    /** Create an unsent draft from one complete supported request. */ API_STUDIO,
    /** Review an unsaved interception rule. */ INTERCEPT,
    /** Review an unsaved shaping rule. */ NETWORK_CONDITIONS,
    /** Review an unsaved destination-handling rule. */ PROTECTED_TRAFFIC,
}

/** Immutable handoff; file metadata never supplies authenticated workspace ownership. */
public sealed interface CaptureImportActionDraft {
    /** A complete text HTTP request; the receiving editor never sends it automatically. */
    public data class ApiStudio(
        /** Complete bounded request values, independent of the retired import spool. */
        public val request: NetworkRequestSpec,
    ) : CaptureImportActionDraft

    /** Protocol-aware interception editor values scoped to the user's captured workspace. */
    public data class Intercept(
        /** New unsaved rule containing canonical destination, method and protocol selectors. */
        public val rule: BreakpointRule,
        /** Registered protocol-specific values for the existing rule editor. */
        public val values: List<ProtocolCriteriaValue>,
        /** App-selected policy workspace, never taken from file metadata. */
        public val workspace: PolicyWorkspace,
    ) : CaptureImportActionDraft

    /** Protocol-aware condition editor values; no profile is activated by this handoff. */
    public data class NetworkConditions(
        /** Canonical destination host and port. */
        public val target: NetworkConditionTarget,
        /** Suggested match scope supported by captured request evidence. */
        public val criteria: NetworkConditionProtocolCriteria,
        /** Registered protocol-specific values for the existing rule editor. */
        public val values: List<ProtocolCriteriaValue>,
        /** App-selected policy workspace, never taken from file metadata. */
        public val workspace: PolicyWorkspace,
    ) : CaptureImportActionDraft

    /** Exact destination values for a manual editor, without importing recorded policy. */
    public data class ProtectedTraffic(
        /** Canonical destination host and port. */
        public val target: NetworkConditionTarget,
        /** App-selected policy workspace, never taken from file metadata. */
        public val workspace: PolicyWorkspace,
    ) : CaptureImportActionDraft
}
