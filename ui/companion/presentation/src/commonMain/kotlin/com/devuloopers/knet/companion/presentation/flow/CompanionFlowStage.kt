package com.devuloopers.knet.companion.presentation.flow

import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.model.CompanionCertificateState

/** Closed set of root companion screens selected from authoritative presentation state. */
public enum class CompanionFlowStage {
    /** No active desktop exists and an invitation must be entered or selected. */
    CONNECT_DESKTOP,

    /** An active desktop still requires certificate download, verification, or explicit continuation. */
    CERTIFICATE_SETUP,

    /** Certificate onboarding was durably completed for the active desktop root. */
    INSPECTION_HOME,
}

/**
 * Resolves the only root screen the current state is allowed to display.
 *
 * The durable enrollment controls onboarding navigation. Live certificate verification remains separate and gates
 * inspection actions, so a temporarily unreachable desktop does not send an already enrolled user through download
 * setup again. An authoritative missing/rejected root does reopen setup, even for a previously enrolled desktop.
 */
public fun CompanionUiState.resolveFlowStage(): CompanionFlowStage = when {
    activeRegistration == null -> CompanionFlowStage.CONNECT_DESKTOP
    certificate == CompanionCertificateState.InstallationRequired || certificate is CompanionCertificateState.Rejected ||
        (certificate is CompanionCertificateState.Trusted && !hasTrustedActiveCertificate) -> CompanionFlowStage.CERTIFICATE_SETUP
    certificateEnrollment?.matches(activeRegistration) == true -> CompanionFlowStage.INSPECTION_HOME
    else -> CompanionFlowStage.CERTIFICATE_SETUP
}
