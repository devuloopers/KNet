package com.devuloopers.knet.traffic.model

/** Policy provenance retained on a flow independently of later rule changes. */
public enum class ProtectionDecisionOrigin {
    /** The global fallback selected the action. */
    GLOBAL,
    /** Temporary client/session-scoped fallback after an explicit certificate rejection. */
    AUTOMATIC,
    /** A user-authored rule selected the action. */
    MANUAL,
    /** An enabled versioned service preset selected the action. */
    PRESET,
    /** Historical automatic compatibility origin; retained for old captures only. */
    SMART,
}

/** Bounded diagnostic classification; certificate rejection is not proof of pinning. */
public enum class InspectionFailureReason {
    /** A supported downstream certificate alert rejected the inspection certificate. */
    CERTIFICATE_REJECTED,
    /** The handshake failed without evidence identifying a more specific cause. */
    UNKNOWN_HANDSHAKE,
    /** Certificate generation or TLS setup failed before a certificate was available. */
    CERTIFICATE_SETUP,
    /** Historical setup-suspicion classification from the retired automatic learner. */
    SETUP_SUSPECTED,
    /** Historical failed-trial classification from the retired automatic learner. */
    TRIAL_FAILED,
}
