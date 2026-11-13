package com.devuloopers.knet.ui.desktop.app.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.devuloopers.knet.ui.desktop.app.workspace.DeviceWorkspaceViewModel
import com.devuloopers.knet.ui.desktop.app.workspace.DeviceWorkspaceTabs
import com.devuloopers.knet.ui.core.foundation.theme.KNetApplicationTheme
import com.devuloopers.knet.ui.desktop.app.dialog.KNetDialogHost
import com.devuloopers.knet.ui.desktop.app.host.KNetApplicationScaffold
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationRail
import com.devuloopers.knet.ui.desktop.app.notification.KNetNotificationHost
import com.devuloopers.knet.ui.desktop.app.overlay.KNetOverlayHost
import com.devuloopers.knet.ui.desktop.app.workspace.KNetWorkspaceHost
import com.devuloopers.knet.ui.core.components.dialog.ConfirmDialog
import com.devuloopers.knet.application.contract.device.DevicePresenceStatus

/**
 * Desktop application entry point composable wrapping the window theme, layout application shell, and feature hosts.
 *
 * @param appState State holder for window title, bounds, and navigation controller.
 * @param modifier Layout modifier.
 */
@Composable
fun MainWindow(
    appState: MainWindowState = rememberMainWindowState(),
    modifier: Modifier = Modifier,
    onTitleBarInteractiveBoundsChanged: (List<androidx.compose.ui.geometry.Rect>) -> Unit = {},
    titleBarInsetStart: Dp = 0.dp,
    titleBarWrapper: @Composable (@Composable () -> Unit) -> Unit = { content -> content() },
) {
    val destination by appState.navigationController.currentDestination.collectAsState()
    val deviceWorkspaces: DeviceWorkspaceViewModel = koinViewModel()
    val devices by deviceWorkspaces.state.collectAsState()
    val traffic: com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel = koinViewModel()
    val trafficState by traffic.uiState.collectAsState()
    KNetApplicationTheme {
        Column(modifier.fillMaxSize()) {
            titleBarWrapper { DeviceWorkspaceTabs(devices, { source -> traffic.selectSource(source); deviceWorkspaces.select(source) },
                Modifier.fillMaxWidth().padding(start = titleBarInsetStart), onTitleBarInteractiveBoundsChanged,
                onClose = { id -> if (!trafficState.isClearingHistory) deviceWorkspaces.requestClose(id) },
                closeInProgress = trafficState.isClearingHistory) }
            KNetApplicationScaffold(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                navigationRail = {
                    NavigationRail(
                        currentDestination = destination,
                        onDestinationSelected = { appState.navigationController.navigate(it) }
                    )
                },
                dialogHost = {
                    KNetDialogHost()
                },
                overlayHost = {
                    KNetOverlayHost()
                },
                notificationHost = {
                    KNetNotificationHost()
                }
            ) {
                KNetWorkspaceHost(
                    destination = destination,
                    onNavigateToDestination = { appState.navigationController.navigate(it) },
                    onClearTraffic = {
                        if (!traffic.uiState.value.isClearingHistory) {
                            deviceWorkspaces.requestClear(traffic.uiState.value.source)?.let(traffic::clearSource)
                        }
                    },
                )
            }
        }
        devices.clearConfirmation?.let { pending ->
            val connected = devices.devices.any { entry ->
                entry.device.id == pending.source.id && entry.status != DevicePresenceStatus.OFFLINE
            }
            ConfirmDialog(
                title = if (connected) "Clear this device's traffic?" else "Clear traffic and close this tab?",
                message = "Remove all locally saved traffic for ${pending.deviceLabel}, including traffic hidden by filters. " +
                    "Its pairing will remain saved." +
                    if (connected) " The device is connected, so its tab will remain visible." else "",
                confirmText = if (connected) "Clear traffic" else "Clear and close",
                onConfirm = { deviceWorkspaces.confirmClear()?.let(traffic::clearSource) },
                onDismissRequest = deviceWorkspaces::dismissClear,
            )
        }
    }
}
