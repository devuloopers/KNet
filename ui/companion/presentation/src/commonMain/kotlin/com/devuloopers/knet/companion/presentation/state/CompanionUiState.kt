package com.devuloopers.knet.companion.presentation.state

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionCertificateEnrollment
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionDesktopAvailability
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionDiscoveryState

/**
 * Immutable companion presentation state rendered by the shared Compose Multiplatform interface.
 *
 * @property registrations durable paired desktops available on this device.
 * @property activeRegistration desktop selected for trust, connection, and inspection workflows.
 * @property invitationScannerVisible whether the Connect screen is displaying its inline camera scanner.
 * @property connection authenticated companion transport state.
 * @property desktopAvailability credential-authenticated reachability of the paired desktop outside inspection.
 * @property inspection native inspection lifecycle state.
 * @property certificate authoritative certificate trust state for the active registration.
 * @property certificateExport current public-certificate file export lifecycle.
 * @property certificateEnrollment durable onboarding completion for the active desktop's exact root.
 * @property network current platform network reachability.
 * @property inspectionPermissionRequired whether the shared VPN explanation must be displayed.
 * @property operationInProgress whether a foreground user operation is currently running.
 * @property failure current presentation-safe recoverable or terminal failure.
 */
public data class CompanionUiState(
    public val registrations: List<CompanionRegistration> = emptyList(),
    public val activeRegistration: CompanionRegistration? = null,
    public val invitationScannerVisible: Boolean = false,
    public val connection: CompanionConnectionState = CompanionConnectionState.Disconnected,
    public val desktopAvailability: CompanionDesktopAvailability = CompanionDesktopAvailability.Idle,
    public val inspection: CompanionInspectionState = CompanionInspectionState.Stopped,
    public val certificate: CompanionCertificateState = CompanionCertificateState.Unknown,
    public val certificateExport: CompanionCertificateExportState = CompanionCertificateExportState.Idle,
    public val certificateEnrollment: CompanionCertificateEnrollment? = null,
    public val network: CompanionNetworkState = CompanionNetworkState.Unknown,
    public val discovery: CompanionDiscoveryState = CompanionDiscoveryState.Idle,
    public val inspectionPermissionRequired: Boolean = false,
    public val operationInProgress: Boolean = false,
    public val failure: CompanionFailure? = null,
)
