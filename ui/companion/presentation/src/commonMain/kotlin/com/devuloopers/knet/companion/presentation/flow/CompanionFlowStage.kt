package com.devuloopers.knet.companion.presentation.flow

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState

/** Closed set of root companion screens selected from authoritative presentation state. */
public enum class CompanionFlowStage {
    /** No active desktop exists and an invitation must be entered or selected. */
    CONNECT_DESKTOP,

    /** The user is scanning a desktop invitation with the platform camera. */
    SCAN_INVITATION,

    /** A validated invitation awaits explicit desktop confirmation. */
    CONFIRM_DESKTOP,

    /** An active desktop exists but its KNet root has not been proven trusted. */
    CERTIFICATE_SETUP,

    /** The product must explain and request native traffic-inspection permission. */
    INSPECTION_PERMISSION,

    /** Pairing and certificate gates are satisfied and normal controls may be displayed. */
    HOME,
}

/**
 * Resolves the only root screen the current state is allowed to display.
 *
 * Pairing and certificate requirements take precedence over restored navigation state. VPN permission appears
 * only while the native controller reports that it is awaiting consent; stopping an already-authorized tunnel
 * therefore returns to Home instead of restarting onboarding.
 */
public fun CompanionUiState.resolveFlowStage(): CompanionFlowStage = when {
    activeRegistration == null && invitationDesktopName != null -> CompanionFlowStage.CONFIRM_DESKTOP
    activeRegistration == null && invitationScannerVisible -> CompanionFlowStage.SCAN_INVITATION
    activeRegistration == null -> CompanionFlowStage.CONNECT_DESKTOP
    certificate !is CompanionCertificateState.Trusted -> CompanionFlowStage.CERTIFICATE_SETUP
    inspectionPermissionRequired -> CompanionFlowStage.INSPECTION_PERMISSION
    else -> CompanionFlowStage.HOME
}
