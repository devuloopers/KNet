package com.devuloopers.knet.companion.android.scanner

import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScannerState

/** Maps Android permission facts to the portable scanner state rendered by shared UI. */
internal fun resolveCameraPermissionState(
    granted: Boolean,
    permissionRequested: Boolean,
    shouldShowRationale: Boolean,
    requestInProgress: Boolean = false,
): CompanionInvitationScannerState = when {
    granted -> CompanionInvitationScannerState.STARTING
    requestInProgress -> CompanionInvitationScannerState.PERMISSION_REQUESTING
    shouldShowRationale -> CompanionInvitationScannerState.PERMISSION_DENIED
    !permissionRequested -> CompanionInvitationScannerState.PERMISSION_REQUIRED
    else -> CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED
}
