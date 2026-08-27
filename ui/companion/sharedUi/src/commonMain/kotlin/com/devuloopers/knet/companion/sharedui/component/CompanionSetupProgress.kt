package com.devuloopers.knet.companion.sharedui.component

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState

/**
 * Semantic onboarding milestones used by the shared companion progress indicator.
 *
 * Animation values are deliberately derived by the UI instead of being stored in presentation state. This keeps
 * domain progress strongly typed while allowing the indicator to animate between milestones independently.
 */
internal enum class CompanionSetupProgress {
    Scanning,
    DesktopConnected,
    CertificateDownloaded,
    CertificateVerified,
}

/** Maps authoritative companion state to one deterministic onboarding milestone. */
internal fun CompanionUiState.toCompanionSetupProgress(): CompanionSetupProgress = when {
    activeRegistration == null -> CompanionSetupProgress.Scanning
    certificateExport !is CompanionCertificateExportState.Saved -> CompanionSetupProgress.DesktopConnected
    certificate is CompanionCertificateState.Trusted -> CompanionSetupProgress.CertificateVerified
    else -> CompanionSetupProgress.CertificateDownloaded
}
