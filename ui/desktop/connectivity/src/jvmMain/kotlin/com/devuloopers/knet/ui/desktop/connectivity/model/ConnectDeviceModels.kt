package com.devuloopers.knet.ui.desktop.connectivity.model

import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.WifiSharingState

/** Connectivity operation whose progress is visible in the Wi-Fi setup presentation. */
enum class ConnectDeviceOperation {
    STARTING_PROXY,
}

/** Drawer currently presented by the extensible Connect Device workspace. */
enum class ConnectDeviceDrawer {
    WIFI_PROXY_SETUP,
    COMPANION_CONNECTION,
}

/** Presentation-safe reason an invitation could not be created. */
enum class CompanionInvitationFailure {
    CONNECTIVITY_UNAVAILABLE,
    CREATION_FAILED,
}

/** Ephemeral desktop presentation state for one secret-bearing companion invitation. */
sealed interface CompanionInvitationUiState {
    /** No invitation exists in memory. */
    data object Idle : CompanionInvitationUiState

    /** A fresh one-time invitation is being created. */
    data object Creating : CompanionInvitationUiState

    /**
     * One active invitation. [qrPayload] contains a short-lived secret and must never be persisted or logged.
     */
    data class Ready(
        val qrPayload: String,
        val desktopDisplayName: String,
        val host: String,
        val controlPort: Int,
        val expiresAtEpochMillis: Long,
        val remainingSeconds: Long,
        val networkVersion: Long,
    ) : CompanionInvitationUiState {
        init {
            require(qrPayload.isNotBlank())
            require(desktopDisplayName.isNotBlank())
            require(host.isNotBlank())
            require(controlPort in 1..65_535)
            require(expiresAtEpochMillis > 0L)
            require(remainingSeconds >= 0L)
            require(networkVersion >= 0L)
        }
    }

    /** The invitation secret has been removed from presentation memory after expiry. */
    data object Expired : CompanionInvitationUiState

    /** Invitation generation failed without exposing exception or secret details. */
    data class Failed(val reason: CompanionInvitationFailure) : CompanionInvitationUiState
}

/** Presentation state for the desktop connectivity-method workspace. */
data class ConnectDeviceUiState(
    val proxyState: ProxyRuntimeState = ProxyRuntimeState.Stopped,
    val sharingState: WifiSharingState = WifiSharingState.Disabled(emptyList()),
    val preferredProxyPort: Int = 8_080,
    val activeDrawer: ConnectDeviceDrawer? = null,
    val companionInvitation: CompanionInvitationUiState = CompanionInvitationUiState.Idle,
    val operation: ConnectDeviceOperation? = null,
    val failureCode: String? = null,
) {
    val activeSharing: WifiSharingState.Active?
        get() = sharingState as? WifiSharingState.Active

    val loopbackProxyPort: Int?
        get() = (proxyState as? ProxyRuntimeState.Running)
            ?.handle
            ?.endpoints
            ?.endpoints
            ?.firstOrNull { it.scope == ProxyEndpointScope.LOOPBACK }
            ?.port

    val isProxyRunning: Boolean
        get() = loopbackProxyPort != null

    val isBusy: Boolean
        get() = operation != null

    val isWifiSetupDrawerVisible: Boolean
        get() = activeDrawer == ConnectDeviceDrawer.WIFI_PROXY_SETUP

    val isCompanionDrawerVisible: Boolean
        get() = activeDrawer == ConnectDeviceDrawer.COMPANION_CONNECTION
}

/** User interactions supported by the desktop connectivity-method workspace. */
sealed interface ConnectDeviceIntent {
    data object OpenWifiSetup : ConnectDeviceIntent
    data object OpenCompanionConnection : ConnectDeviceIntent
    data object RefreshCompanionInvitation : ConnectDeviceIntent
    data object CloseDrawer : ConnectDeviceIntent
    data object StartProxy : ConnectDeviceIntent
}
