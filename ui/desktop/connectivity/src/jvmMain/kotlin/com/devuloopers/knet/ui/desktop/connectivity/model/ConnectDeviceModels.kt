package com.devuloopers.knet.ui.desktop.connectivity.model

import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.WifiSharingState

/** Connectivity operation whose progress is visible in the Wi-Fi setup presentation. */
enum class ConnectDeviceOperation {
    STARTING_PROXY,
}

/** Presentation state for the single-card Wi-Fi proxy setup workflow. */
data class ConnectDeviceUiState(
    val proxyState: ProxyRuntimeState = ProxyRuntimeState.Stopped,
    val sharingState: WifiSharingState = WifiSharingState.Disabled(emptyList()),
    val preferredProxyPort: Int = 8_080,
    val isSetupDrawerVisible: Boolean = false,
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
}

/** User interactions supported by the focused Wi-Fi proxy setup screen. */
sealed interface ConnectDeviceIntent {
    data object OpenSetup : ConnectDeviceIntent
    data object CloseSetup : ConnectDeviceIntent
    data object StartProxy : ConnectDeviceIntent
}
