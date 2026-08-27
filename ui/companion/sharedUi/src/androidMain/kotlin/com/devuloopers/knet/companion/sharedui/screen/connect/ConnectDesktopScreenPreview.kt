package com.devuloopers.knet.companion.sharedui.screen.connect

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScannerState
import com.devuloopers.knet.companion.sharedui.scanner.UnavailableCompanionInvitationScanner
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ready state used for the primary Connect-screen design review. */
@Preview(
    name = "Connect - Ready - Dark",
    group = "Connect",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ConnectReadyDarkPreview() {
    ConnectPreview(
        state = CompanionUiState(network = CompanionNetworkState.Available(metered = false)),
        themeMode = ThemeMode.Dark,
    )
}

/** Initial reactive state; the reserved feedback region shows its shimmer without shifting content. */
@Preview(
    name = "Connect - Checking network",
    group = "Connect states",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ConnectCheckingNetworkPreview() {
    ConnectPreview(
        state = CompanionUiState(network = CompanionNetworkState.Unknown),
        themeMode = ThemeMode.Dark,
    )
}

/** Offline state with the scan action disabled and recovery guidance kept in-place. */
@Preview(
    name = "Connect - Network unavailable",
    group = "Connect states",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ConnectNetworkUnavailablePreview() {
    ConnectPreview(
        state = CompanionUiState(network = CompanionNetworkState.Unavailable),
        themeMode = ThemeMode.Dark,
    )
}

/** Camera startup shimmer occupying exactly the same panel as the illustration and live preview. */
@Preview(
    name = "Connect - Inline scanner starting",
    group = "Connect scanner",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ConnectScanStartingPreview() {
    ConnectPreview(
        state = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
            invitationScannerVisible = true,
        ),
        themeMode = ThemeMode.Dark,
        scanner = ConnectPreviewScanner(CompanionInvitationScannerState.STARTING),
    )
}

/** Active inline camera state; the surrounding Connect card remains unchanged. */
@Preview(
    name = "Connect - Inline scanner active",
    group = "Connect scanner",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ConnectInlineScannerActivePreview() {
    ConnectPreview(
        state = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
            invitationScannerVisible = true,
        ),
        themeMode = ThemeMode.Dark,
        scanner = ConnectPreviewScanner(CompanionInvitationScannerState.ACTIVE),
    )
}

/** Permission guidance rendered inside the same visual panel instead of a separate screen. */
@Preview(
    name = "Connect - Inline camera permission",
    group = "Connect scanner",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ConnectInlineScannerPermissionPreview() {
    ConnectPreview(
        state = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
            invitationScannerVisible = true,
        ),
        themeMode = ThemeMode.Dark,
        scanner = ConnectPreviewScanner(CompanionInvitationScannerState.PERMISSION_REQUIRED),
    )
}

/** Secure QR resolution replaces the camera in-place without changing the route or card geometry. */
@Preview(
    name = "Connect - QR resolving",
    group = "Connect scanner",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ConnectQrResolvingPreview() {
    ConnectPreview(
        state = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
            invitationScannerVisible = true,
            operationInProgress = true,
        ),
        themeMode = ThemeMode.Dark,
        scanner = ConnectPreviewScanner(CompanionInvitationScannerState.ACTIVE),
    )
}

/** Invalid QR failure remains on the mounted scanner route with wrapping recovery content below the action. */
@Preview(
    name = "Connect - Invalid QR failure",
    group = "Connect failures",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ConnectFailurePreview() {
    ConnectPreview(
        state = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
            invitationScannerVisible = true,
            failure = CompanionFailure(
                code = CompanionFailureCode.INVITATION_INVALID,
                message = "The QR code is not a supported KNet Desktop invitation.",
                recoverable = true,
            ),
        ),
        themeMode = ThemeMode.Dark,
        scanner = ConnectPreviewScanner(CompanionInvitationScannerState.ACTIVE),
    )
}

/** Valid KNet QR whose desktop cannot be reached from the current local network. */
@Preview(
    name = "Connect - Desktop unreachable",
    group = "Connect failures",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ConnectDesktopUnreachablePreview() {
    ConnectPreview(
        state = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
            invitationScannerVisible = true,
            failure = CompanionFailure(
                code = CompanionFailureCode.INVITATION_RETRIEVAL_FAILED,
                message = "Unable to retrieve the invitation from KNet Desktop.",
                recoverable = true,
            ),
        ),
        themeMode = ThemeMode.Dark,
        scanner = ConnectPreviewScanner(CompanionInvitationScannerState.ACTIVE),
    )
}

/** Non-retryable identity mismatch still permits scanning a different freshly generated invitation. */
@Preview(
    name = "Connect - Security failure",
    group = "Connect failures",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ConnectSecurityFailurePreview() {
    ConnectPreview(
        state = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
            invitationScannerVisible = true,
            failure = CompanionFailure(
                code = CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
                message = "The desktop identity did not match the scanned invitation.",
                recoverable = false,
            ),
        ),
        themeMode = ThemeMode.Dark,
        scanner = ConnectPreviewScanner(CompanionInvitationScannerState.ACTIVE),
    )
}

/** Compact light-theme coverage for smaller phones and responsive vertical scrolling. */
@Preview(
    name = "Connect - Compact - Light",
    group = "Connect responsive",
    widthDp = 320,
    heightDp = 640,
    showBackground = true,
)
@Composable
private fun ConnectCompactLightPreview() {
    ConnectPreview(
        state = CompanionUiState(network = CompanionNetworkState.Available(metered = true)),
        themeMode = ThemeMode.Light,
    )
}

@Composable
private fun ConnectPreview(
    state: CompanionUiState,
    themeMode: ThemeMode,
    scanner: CompanionInvitationScanner = UnavailableCompanionInvitationScanner,
) {
    KNetTheme(themeMode = themeMode) {
        ConnectDesktopScreen(state = state, scanner = scanner, onAction = {})
    }
}

private class ConnectPreviewScanner(
    initialState: CompanionInvitationScannerState,
) : CompanionInvitationScanner {
    override val state: StateFlow<CompanionInvitationScannerState> = MutableStateFlow(initialState)

    override fun requestCameraPermission(): Unit = Unit

    override fun openApplicationSettings(): Unit = Unit

    @Composable
    override fun Preview(onPayloadDetected: (String) -> Unit, modifier: Modifier) {
        Box(
            modifier = modifier.background(KNetTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Live camera preview",
                style = KNetTheme.typography.bodySmall,
                color = KNetTheme.colors.textMuted,
            )
        }
    }

    override fun close(): Unit = Unit
}
