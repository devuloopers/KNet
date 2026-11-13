package com.devuloopers.knet.companion.android.scanner

import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScannerState
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraPermissionStateResolverTest {
    @Test
    fun grantedPermissionStartsTheScannerRegardlessOfEarlierRequests() {
        assertEquals(
            CompanionInvitationScannerState.STARTING,
            resolveCameraPermissionState(
                granted = true,
                permissionRequested = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun initialAndDeniedPermissionStatesRemainDistinct() {
        assertEquals(
            CompanionInvitationScannerState.PERMISSION_REQUIRED,
            resolveCameraPermissionState(
                granted = false,
                permissionRequested = false,
                shouldShowRationale = false,
            ),
        )
        assertEquals(
            CompanionInvitationScannerState.PERMISSION_DENIED,
            resolveCameraPermissionState(
                granted = false,
                permissionRequested = true,
                shouldShowRationale = true,
            ),
        )
        assertEquals(
            CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED,
            resolveCameraPermissionState(
                granted = false,
                permissionRequested = true,
                shouldShowRationale = false,
            ),
        )
    }
    @Test
    fun nativePromptInFlightCannotBeMistakenForADenialDuringResume() {
        assertEquals(CompanionInvitationScannerState.PERMISSION_REQUESTING,
            resolveCameraPermissionState(false, true, false, requestInProgress = true))
        assertEquals(CompanionInvitationScannerState.PERMISSION_REQUESTING,
            resolveCameraPermissionState(false, true, true, requestInProgress = true))
    }

    @Test
    fun platformRationaleAndRestoredUnresolvedHistoryRemainAuthoritative() {
        assertEquals(CompanionInvitationScannerState.PERMISSION_DENIED,
            resolveCameraPermissionState(false, false, true))
        assertEquals(CompanionInvitationScannerState.PERMISSION_PERMANENTLY_DENIED,
            resolveCameraPermissionState(false, true, false))
        // A grant clears unresolved history; expiry of a one-time grant can then request permission again.
        assertEquals(CompanionInvitationScannerState.PERMISSION_REQUIRED,
            resolveCameraPermissionState(false, false, false))
    }

}
