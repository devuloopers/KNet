package com.devuloopers.knet.companion.presentation.state

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionConnectionState
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
 * @property invitationDesktopName display name from a validated invitation retained only in memory.
 * @property invitationScannerVisible whether the shared camera-scanner route is currently active.
 * @property pairingInProgress whether the pairing exchange is currently running.
 * @property connection authenticated companion transport state.
 * @property inspection native inspection lifecycle state.
 * @property certificate authoritative certificate trust state for the active registration.
 * @property certificateExport current public-certificate file export lifecycle.
 * @property network current platform network reachability.
 * @property inspectionPermissionRequired whether the shared VPN explanation must be displayed.
 * @property operationInProgress whether a foreground user operation is currently running.
 * @property failure current presentation-safe recoverable or terminal failure.
 */
public data class CompanionUiState(
    public val registrations: List<CompanionRegistration> = emptyList(),
    public val activeRegistration: CompanionRegistration? = null,
    public val invitationDesktopName: String? = null,
    public val invitationScannerVisible: Boolean = false,
    public val pairingInProgress: Boolean = false,
    public val connection: CompanionConnectionState = CompanionConnectionState.Disconnected,
    public val inspection: CompanionInspectionState = CompanionInspectionState.Stopped,
    public val certificate: CompanionCertificateState = CompanionCertificateState.Unknown,
    public val certificateExport: CompanionCertificateExportState = CompanionCertificateExportState.Idle,
    public val network: CompanionNetworkState = CompanionNetworkState.Unknown,
    public val discovery: CompanionDiscoveryState = CompanionDiscoveryState.Idle,
    public val inspectionPermissionRequired: Boolean = false,
    public val operationInProgress: Boolean = false,
    public val failure: CompanionFailure? = null,
)
