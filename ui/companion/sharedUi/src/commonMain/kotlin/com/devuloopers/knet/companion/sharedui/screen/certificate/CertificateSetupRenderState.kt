package com.devuloopers.knet.companion.sharedui.screen.certificate

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState

/** Strongly typed visual phase for the reactive certificate setup route. */
internal sealed interface CertificateSetupPhase {
    data object Download : CertificateSetupPhase

    data class Installation(
        val savedExport: CompanionCertificateExportState.Saved,
    ) : CertificateSetupPhase

    data class Verified(
        val savedExport: CompanionCertificateExportState.Saved,
    ) : CertificateSetupPhase
}

/** Closed set of trust feedback states rendered without changing installation-card height. */
internal sealed interface CertificateVerificationRenderState {
    data object Waiting : CertificateVerificationRenderState

    data object Verifying : CertificateVerificationRenderState

    data object Trusted : CertificateVerificationRenderState

    data class Rejected(val message: String) : CertificateVerificationRenderState

    data class Failed(val failure: CompanionFailure) : CertificateVerificationRenderState
}

/** Stable interaction flags consumed consistently by every certificate setup component. */
internal data class CertificateSetupRenderState(
    val phase: CertificateSetupPhase,
    val downloadInProgress: Boolean,
    val verificationInProgress: Boolean,
    val verification: CertificateVerificationRenderState,
) {
    val busy: Boolean = downloadInProgress || verificationInProgress

    val canContinue: Boolean = phase is CertificateSetupPhase.Verified &&
        verification is CertificateVerificationRenderState.Trusted &&
        !busy
}

internal fun CompanionUiState.toCertificateSetupRenderState(): CertificateSetupRenderState {
    val export = certificateExport
    val saved = export as? CompanionCertificateExportState.Saved
    val currentCertificate = certificate
    val currentFailure = failure
    return CertificateSetupRenderState(
        phase = when {
            saved == null -> CertificateSetupPhase.Download
            currentCertificate is CompanionCertificateState.Trusted -> CertificateSetupPhase.Verified(saved)
            else -> CertificateSetupPhase.Installation(saved)
        },
        downloadInProgress = export is CompanionCertificateExportState.Saving,
        verificationInProgress = operationInProgress || currentCertificate is CompanionCertificateState.Verifying,
        verification = when {
            currentFailure != null -> CertificateVerificationRenderState.Failed(currentFailure)
            currentCertificate is CompanionCertificateState.Verifying -> CertificateVerificationRenderState.Verifying
            currentCertificate is CompanionCertificateState.Trusted -> CertificateVerificationRenderState.Trusted
            currentCertificate is CompanionCertificateState.Rejected -> {
                CertificateVerificationRenderState.Rejected(currentCertificate.reason.message)
            }
            else -> CertificateVerificationRenderState.Waiting
        },
    )
}
