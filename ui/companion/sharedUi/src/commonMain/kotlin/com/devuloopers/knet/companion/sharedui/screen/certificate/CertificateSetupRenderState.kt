package com.devuloopers.knet.companion.sharedui.screen.certificate

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState

/** Strongly typed visual phase for the reactive certificate setup route. */
internal sealed interface CertificateSetupPhase {
    /** Initial trust evaluation, before offering any certificate export. */
    data object Checking : CertificateSetupPhase

    /** A failed or deferred check requires an explicit retry rather than assuming a missing certificate. */
    data object VerificationUnavailable : CertificateSetupPhase

    data object Download : CertificateSetupPhase

    data class Installation(
        val savedExport: CompanionCertificateExportState.Saved,
    ) : CertificateSetupPhase

    data class Verified(
        val savedExport: CompanionCertificateExportState.Saved?,
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

    val canContinue: Boolean = phase is CertificateSetupPhase.Verified && !busy
}

internal fun CompanionUiState.toCertificateSetupRenderState(): CertificateSetupRenderState {
    val export = certificateExport
    val saved = (export as? CompanionCertificateExportState.Saved)
        ?.takeIf { it.desktopId == activeRegistration?.desktopId }
    val currentCertificate = certificate
    val currentFailure = failure
    val checking = currentCertificate == CompanionCertificateState.Unknown ||
        currentCertificate == CompanionCertificateState.Verifying
    return CertificateSetupRenderState(
        phase = when {
            hasTrustedActiveCertificate -> CertificateSetupPhase.Verified(saved)
            export is CompanionCertificateExportState.Saving -> CertificateSetupPhase.Download
            checking && currentFailure == null -> saved?.let { CertificateSetupPhase.Installation(it) }
                ?: CertificateSetupPhase.Checking
            currentCertificate == CompanionCertificateState.InstallationRequired ->
                saved?.let { CertificateSetupPhase.Installation(it) } ?: CertificateSetupPhase.Download
            else -> CertificateSetupPhase.VerificationUnavailable
        },
        downloadInProgress = export is CompanionCertificateExportState.Saving,
        verificationInProgress = operationInProgress || (checking && currentFailure == null),
        verification = when {
            currentFailure != null -> CertificateVerificationRenderState.Failed(currentFailure)
            checking -> CertificateVerificationRenderState.Verifying
            currentCertificate is CompanionCertificateState.VerificationDeferred ->
                CertificateVerificationRenderState.Failed(currentCertificate.reason)
            hasTrustedActiveCertificate -> CertificateVerificationRenderState.Trusted
            currentCertificate is CompanionCertificateState.Trusted -> CertificateVerificationRenderState.Rejected(
                "The verified certificate does not match the paired desktop.",
            )
            currentCertificate is CompanionCertificateState.Rejected -> {
                CertificateVerificationRenderState.Rejected(currentCertificate.reason.message)
            }
            else -> CertificateVerificationRenderState.Waiting
        },
    )
}
