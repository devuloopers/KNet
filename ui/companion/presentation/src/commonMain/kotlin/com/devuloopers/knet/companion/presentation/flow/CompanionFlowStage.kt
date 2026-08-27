package com.devuloopers.knet.companion.presentation.flow

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateSetupAcknowledgement
import com.devuloopers.knet.companion.presentation.state.CompanionUiState

/** Closed set of root companion screens selected from authoritative presentation state. */
public enum class CompanionFlowStage {
    /** No active desktop exists and an invitation must be entered or selected. */
    CONNECT_DESKTOP,

    /** An active desktop still requires certificate download, verification, or explicit continuation. */
    CERTIFICATE_SETUP,

    /** Verified setup was explicitly acknowledged and operational inspection controls may be shown. */
    INSPECTION_HOME,
}

/**
 * Resolves the only root screen the current state is allowed to display.
 *
 * Pairing and certificate trust take precedence over restored navigation state. Home is allowed only when the
 * expected certificate was saved, authoritatively verified, and explicitly acknowledged by the user.
 */
public fun CompanionUiState.resolveFlowStage(): CompanionFlowStage = when {
    activeRegistration == null -> CompanionFlowStage.CONNECT_DESKTOP
    certificateSetupAcknowledgement == CompanionCertificateSetupAcknowledgement.ACKNOWLEDGED &&
        certificate is CompanionCertificateState.Trusted &&
        certificateExport is CompanionCertificateExportState.Saved ->
        CompanionFlowStage.INSPECTION_HOME
    else -> CompanionFlowStage.CERTIFICATE_SETUP
}
