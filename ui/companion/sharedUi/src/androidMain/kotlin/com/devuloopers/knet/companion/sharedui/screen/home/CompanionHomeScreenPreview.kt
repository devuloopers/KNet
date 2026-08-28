package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionMode
import com.devuloopers.knet.companion.presentation.state.CompanionHomeCertificateStatus
import com.devuloopers.knet.companion.presentation.state.CompanionHomeDesktopStatus
import com.devuloopers.knet.companion.presentation.state.CompanionHomeFailureNotice
import com.devuloopers.knet.companion.presentation.state.CompanionHomeHttpsCapability
import com.devuloopers.knet.companion.presentation.state.CompanionHomeInspectionControl
import com.devuloopers.knet.companion.presentation.state.CompanionHomeNetworkPath
import com.devuloopers.knet.companion.presentation.state.CompanionHomeReadiness
import com.devuloopers.knet.companion.presentation.state.CompanionHomeTunnelStatus
import com.devuloopers.knet.companion.presentation.state.CompanionHomeUiState
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode

@Preview(name = "Home - Ready", group = "Home", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun HomeReadyPreview() {
    HomePreview(readyHome())
}

@Preview(name = "Home - Running", group = "Home states", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun HomeRunningPreview() {
    HomePreview(
        readyHome().copy(
            readiness = CompanionHomeReadiness.ACTIVE,
            inspectionControl = CompanionHomeInspectionControl.Stop,
            tunnelStatus = CompanionHomeTunnelStatus.ACTIVE,
        ),
    )
}

@Preview(name = "Home - Unavailable", group = "Home states", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun HomeUnavailablePreview() {
    HomePreview(
        readyHome().copy(
            readiness = CompanionHomeReadiness.UNAVAILABLE,
            inspectionControl = CompanionHomeInspectionControl.Start(enabled = false),
            desktopStatus = CompanionHomeDesktopStatus.UNAVAILABLE,
            networkPath = CompanionHomeNetworkPath.UNAVAILABLE,
            failureNotice = CompanionHomeFailureNotice.Persistent(
                CompanionFailure(
                    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
                    "KNet Desktop is not reachable on this local network.",
                    true,
                ),
            ),
        ),
    )
}

@Preview(name = "Home - Compact light", group = "Home responsive", widthDp = 320, heightDp = 640, showBackground = true)
@Composable
private fun HomeCompactLightPreview() {
    HomePreview(readyHome(), ThemeMode.Light)
}

@Composable
private fun HomePreview(state: CompanionHomeUiState, themeMode: ThemeMode = ThemeMode.Dark) {
    KNetTheme(themeMode = themeMode) {
        CompanionHomeScreen(state = state, onAction = {})
    }
}

private fun readyHome(): CompanionHomeUiState = CompanionHomeUiState(
    desktopDisplayName = "KNet Desktop",
    readiness = CompanionHomeReadiness.READY,
    inspectionControl = CompanionHomeInspectionControl.Start(enabled = true),
    desktopStatus = CompanionHomeDesktopStatus.AVAILABLE,
    certificateStatus = CompanionHomeCertificateStatus.VERIFIED,
    tunnelStatus = CompanionHomeTunnelStatus.INACTIVE,
    inspectionMode = CompanionInspectionMode.DEVICE_VPN,
    networkPath = CompanionHomeNetworkPath.DIRECT_LAN,
    httpsCapability = CompanionHomeHttpsCapability.FULL,
    failureNotice = null,
)
