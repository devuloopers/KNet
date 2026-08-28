package com.devuloopers.knet.companion.presentation.state

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionDesktopAvailability
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionInspectionMode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionTransportKind

/** Overall operational readiness shown by Home. */
public enum class CompanionHomeReadiness {
    CHECKING,
    READY,
    PREPARING,
    ACTIVE,
    UNAVAILABLE,
    NEEDS_ATTENTION,
}

/** Credential-authenticated state of the selected desktop. */
public enum class CompanionHomeDesktopStatus {
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
    SECURITY_FAILURE,
}

/** Authoritative certificate state summarized by Home. */
public enum class CompanionHomeCertificateStatus {
    CHECKING,
    VERIFICATION_PENDING,
    VERIFIED,
    NEEDS_ATTENTION,
}

/** Runtime state of the device-to-desktop secure inspection tunnel. */
public enum class CompanionHomeTunnelStatus {
    INACTIVE,
    CONNECTING,
    ACTIVE,
    RECONNECTING,
    FAILED,
}

/** Network route selected for the current or next inspection. */
public enum class CompanionHomeNetworkPath {
    DIRECT_LAN,
    RELAY,
    UNAVAILABLE,
}

/** HTTPS visibility supported by the current trusted configuration. */
public enum class CompanionHomeHttpsCapability {
    FULL,
    LIMITED,
}

/** Closed set of primary inspection controls, including their interaction policy. */
public sealed interface CompanionHomeInspectionControl {
    public data class Start(public val enabled: Boolean) : CompanionHomeInspectionControl
    public data object Starting : CompanionHomeInspectionControl
    public data object ContinueVpnSetup : CompanionHomeInspectionControl
    public data object Stop : CompanionHomeInspectionControl
    public data object Stopping : CompanionHomeInspectionControl
    public data class Retry(public val enabled: Boolean) : CompanionHomeInspectionControl
}

/** Failure notice behavior derived from whether the underlying state can actually be cleared by presentation. */
public sealed interface CompanionHomeFailureNotice {
    public val failure: CompanionFailure

    /** A presentation-only failure that the user may remove from the current screen. */
    public data class Dismissible(override val failure: CompanionFailure) : CompanionHomeFailureNotice

    /** An authoritative runtime failure that remains until its underlying condition changes. */
    public data class Persistent(override val failure: CompanionFailure) : CompanionHomeFailureNotice
}

/** Render-ready, strongly typed Home projection derived only from authoritative companion state. */
public data class CompanionHomeUiState(
    public val desktopDisplayName: String,
    public val readiness: CompanionHomeReadiness,
    public val inspectionControl: CompanionHomeInspectionControl,
    public val desktopStatus: CompanionHomeDesktopStatus,
    public val certificateStatus: CompanionHomeCertificateStatus,
    public val tunnelStatus: CompanionHomeTunnelStatus,
    public val inspectionMode: CompanionInspectionMode,
    public val networkPath: CompanionHomeNetworkPath,
    public val httpsCapability: CompanionHomeHttpsCapability,
    public val failureNotice: CompanionHomeFailureNotice?,
)

/** Converts aggregate presentation state into the only state shape Home is permitted to render. */
public fun CompanionUiState.toCompanionHomeUiState(): CompanionHomeUiState {
    val certificateStatus = when (certificate) {
        is CompanionCertificateState.Trusted -> CompanionHomeCertificateStatus.VERIFIED
        CompanionCertificateState.Unknown,
        CompanionCertificateState.Verifying,
        -> CompanionHomeCertificateStatus.CHECKING
        is CompanionCertificateState.VerificationDeferred -> CompanionHomeCertificateStatus.VERIFICATION_PENDING
        CompanionCertificateState.InstallationRequired,
        is CompanionCertificateState.Rejected,
        -> CompanionHomeCertificateStatus.NEEDS_ATTENTION
    }
    val desktopStatus = when {
        inspection is CompanionInspectionState.Running || connection is CompanionConnectionState.Connected ->
            CompanionHomeDesktopStatus.AVAILABLE
        desktopAvailability is CompanionDesktopAvailability.Available -> CompanionHomeDesktopStatus.AVAILABLE
        desktopAvailability is CompanionDesktopAvailability.Unavailable -> CompanionHomeDesktopStatus.UNAVAILABLE
        desktopAvailability is CompanionDesktopAvailability.Failed -> CompanionHomeDesktopStatus.SECURITY_FAILURE
        else -> CompanionHomeDesktopStatus.CHECKING
    }
    val tunnelStatus = when {
        inspection is CompanionInspectionState.Running -> CompanionHomeTunnelStatus.ACTIVE
        connection is CompanionConnectionState.Connecting -> CompanionHomeTunnelStatus.CONNECTING
        connection is CompanionConnectionState.Connected -> CompanionHomeTunnelStatus.ACTIVE
        connection is CompanionConnectionState.Reconnecting -> CompanionHomeTunnelStatus.RECONNECTING
        connection is CompanionConnectionState.Failed -> CompanionHomeTunnelStatus.FAILED
        else -> CompanionHomeTunnelStatus.INACTIVE
    }
    val readiness = when {
        inspection is CompanionInspectionState.Running -> CompanionHomeReadiness.ACTIVE
        inspection == CompanionInspectionState.Preparing ||
        inspection == CompanionInspectionState.AwaitingVpnConsent ||
            inspection == CompanionInspectionState.Stopping || operationInProgress -> CompanionHomeReadiness.PREPARING
        inspection is CompanionInspectionState.Failed -> CompanionHomeReadiness.NEEDS_ATTENTION
        certificateStatus == CompanionHomeCertificateStatus.CHECKING -> CompanionHomeReadiness.CHECKING
        certificateStatus == CompanionHomeCertificateStatus.NEEDS_ATTENTION -> CompanionHomeReadiness.NEEDS_ATTENTION
        desktopStatus == CompanionHomeDesktopStatus.AVAILABLE && network is CompanionNetworkState.Available ->
            if (certificateStatus == CompanionHomeCertificateStatus.VERIFIED) {
                CompanionHomeReadiness.READY
            } else {
                CompanionHomeReadiness.CHECKING
            }
        desktopStatus == CompanionHomeDesktopStatus.CHECKING || network == CompanionNetworkState.Unknown ->
            CompanionHomeReadiness.CHECKING
        desktopStatus == CompanionHomeDesktopStatus.SECURITY_FAILURE -> CompanionHomeReadiness.NEEDS_ATTENTION
        else -> CompanionHomeReadiness.UNAVAILABLE
    }
    val inspectionControl = when {
        inspection is CompanionInspectionState.Running -> CompanionHomeInspectionControl.Stop
        inspection == CompanionInspectionState.Stopping -> CompanionHomeInspectionControl.Stopping
        inspection == CompanionInspectionState.AwaitingVpnConsent || inspectionPermissionRequired ->
            CompanionHomeInspectionControl.ContinueVpnSetup
        inspection == CompanionInspectionState.Preparing || operationInProgress -> CompanionHomeInspectionControl.Starting
        inspection is CompanionInspectionState.Failed ->
            CompanionHomeInspectionControl.Retry(inspection.failure.recoverable)
        else -> CompanionHomeInspectionControl.Start(
            enabled = readiness == CompanionHomeReadiness.READY,
        )
    }
    val networkPath = when (val currentConnection = connection) {
        is CompanionConnectionState.Connected -> when (currentConnection.transport) {
            CompanionTransportKind.DIRECT_LAN -> CompanionHomeNetworkPath.DIRECT_LAN
            CompanionTransportKind.RELAY -> CompanionHomeNetworkPath.RELAY
        }
        else -> if (desktopStatus == CompanionHomeDesktopStatus.AVAILABLE) {
            CompanionHomeNetworkPath.DIRECT_LAN
        } else {
            CompanionHomeNetworkPath.UNAVAILABLE
        }
    }
    val inspectionFailure = (inspection as? CompanionInspectionState.Failed)?.failure
    val connectionFailure = (connection as? CompanionConnectionState.Failed)?.failure
    val availabilityFailure = when (val availability = desktopAvailability) {
        is CompanionDesktopAvailability.Failed -> availability.failure
        is CompanionDesktopAvailability.Unavailable -> availability.reason
        else -> null
    }
    val failureNotice = when {
        availabilityFailure != null && desktopStatus == CompanionHomeDesktopStatus.UNAVAILABLE ->
            CompanionHomeFailureNotice.Persistent(availabilityFailure)
        failure != null -> CompanionHomeFailureNotice.Dismissible(failure)
        inspectionFailure != null -> CompanionHomeFailureNotice.Persistent(inspectionFailure)
        connectionFailure != null -> CompanionHomeFailureNotice.Persistent(connectionFailure)
        availabilityFailure != null -> CompanionHomeFailureNotice.Persistent(availabilityFailure)
        else -> null
    }
    return CompanionHomeUiState(
        desktopDisplayName = activeRegistration?.desktopDisplayName?.value.orEmpty(),
        readiness = readiness,
        inspectionControl = inspectionControl,
        desktopStatus = desktopStatus,
        certificateStatus = certificateStatus,
        tunnelStatus = tunnelStatus,
        inspectionMode = (inspection as? CompanionInspectionState.Running)?.mode ?: CompanionInspectionMode.DEVICE_VPN,
        networkPath = networkPath,
        httpsCapability = if (
            (inspection as? CompanionInspectionState.Running)?.fullHttpsInspection != false &&
            certificateStatus == CompanionHomeCertificateStatus.VERIFIED
        ) {
            CompanionHomeHttpsCapability.FULL
        } else {
            CompanionHomeHttpsCapability.LIMITED
        },
        failureNotice = failureNotice,
    )
}
