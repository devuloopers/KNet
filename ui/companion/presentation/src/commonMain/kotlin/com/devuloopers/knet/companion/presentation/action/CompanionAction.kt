package com.devuloopers.knet.companion.presentation.action

import com.devuloopers.knet.companion.model.CompanionDesktopId

/** User intents accepted by the shared companion ViewModel. */
public sealed interface CompanionAction {
    /** Opens the shared invitation scanner route. */
    public data object ScanInvitationRequested : CompanionAction

    /** Opens the product-owned QR image picker as a camera-independent fallback. */
    public data object ImportInvitationImageRequested : CompanionAction

    /** Submits the first QR payload detected by the active camera scanner session. */
    public data class InvitationScanned(public val payload: String) : CompanionAction

    /** Leaves the active camera scanner without submitting an invitation. */
    public data object InvitationScannerDismissed : CompanionAction

    /** Validates one scanned or pasted invitation payload. */
    public data class InvitationSubmitted(public val payload: String) : CompanionAction

    /** Discards the currently validated invitation without persisting it. */
    public data object InvitationDismissed : CompanionAction

    /** Completes pairing with a user-visible device name. */
    public data class PairSubmitted(public val deviceDisplayName: String) : CompanionAction

    /** Makes one durable desktop registration active. */
    public data class RegistrationSelected(public val desktopId: CompanionDesktopId) : CompanionAction

    /** Starts the inspection workflow, requesting native consent when required. */
    public data object StartInspectionRequested : CompanionAction

    /** Requests the native VPN consent surface after the shared explanation screen is visible. */
    public data object VpnConsentRequested : CompanionAction

    /** Returns the product-owned VPN consent result to the shared workflow. */
    public data class VpnConsentResolved(public val granted: Boolean) : CompanionAction

    /** Leaves the inspection permission explanation without granting access. */
    public data object InspectionPermissionDismissed : CompanionAction

    /** Stops inspection while retaining the active pairing. */
    public data object StopInspectionRequested : CompanionAction

    /** Downloads and exports the expected public KNet root to a user-visible platform location. */
    public data object DownloadCertificateRequested : CompanionAction

    /** Reports that the platform saved the requested public root to a user-visible location. */
    public data class CertificateExportCompleted(
        public val desktopId: CompanionDesktopId,
        public val fileName: String,
        public val locationDescription: String,
    ) : CompanionAction

    /** Reports that the platform could not save the requested public root. */
    public data class CertificateExportFailed(public val desktopId: CompanionDesktopId) : CompanionAction

    /** Reports that the user cancelled a platform-owned destination picker. */
    public data class CertificateExportCancelled(public val desktopId: CompanionDesktopId) : CompanionAction

    /** Rechecks certificate trust through the authoritative TLS challenge. */
    public data object VerifyCertificateTrustRequested : CompanionAction

    /** Opens the native certificate trust guidance surface. */
    public data object OpenCertificateTrustSettingsRequested : CompanionAction

    /** Rotates the active desktop credential. */
    public data object RefreshCredentialRequested : CompanionAction

    /** Removes one desktop registration and its protected credential. */
    public data class ForgetDesktopRequested(public val desktopId: CompanionDesktopId) : CompanionAction

    /** Clears the currently displayed recoverable failure. */
    public data object ClearFailure : CompanionAction
}
