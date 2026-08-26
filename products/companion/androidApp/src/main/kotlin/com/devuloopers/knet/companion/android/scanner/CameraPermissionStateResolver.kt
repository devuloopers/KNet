package com.devuloopers.knet.companion.android.scanner

import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScannerState

/** Maps Android permission facts to the portable scanner state rendered by shared UI. */
internal fun resolveCameraPermissionState(
    granted: Boolean,
    permissionRequested: Boolean,
    shouldShowRationale: Boolean,
): CompanionInvitationScannerState = when {
    granted -> CompanionInvitationScannerState.STARTING
    !permissionRequested -> CompanionInvitationScannerState.PERMISSION_REQUIRED
    shouldShowRationale -> CompanionInvitationScannerState.PERMISSION_DENIED
    else -> CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED
}
