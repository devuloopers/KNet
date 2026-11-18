package com.devuloopers.knet.ui.desktop.app.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    titleBarInsetEnd: Dp = 0.dp,
    titleBarWrapper: @Composable (@Composable () -> Unit) -> Unit = { content -> content() },
) {
    val destination by appState.navigationController.currentDestination.collectAsState()
    val deviceWorkspaces: DeviceWorkspaceViewModel = koinViewModel()
    val devices by deviceWorkspaces.navigationState.collectAsState()
    val traffic: com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel = koinViewModel()
    val trafficClearing by remember(traffic) { com.devuloopers.knet.application.contract.device.ProjectedStateFlow(traffic.uiState) { it.isClearingHistory } }.collectAsState()
    val breakpointManager: com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel = koinViewModel()
    val pendingCounts by remember(breakpointManager) { com.devuloopers.knet.application.contract.device.ProjectedStateFlow(breakpointManager.uiState) { it.pendingByWorkspace } }.collectAsState()
    val captureImport: com.devuloopers.knet.ui.desktop.traffic.importcapture.CaptureImportDialogViewModel = koinViewModel(key = "capture-import-dialog")
    val importWorkspace = when (val source = devices.source) {
        is com.devuloopers.knet.application.contract.traffic.TrafficSource.Device -> com.devuloopers.knet.domain.policy.PolicyWorkspace.Device(source.id)
        else -> com.devuloopers.knet.domain.policy.PolicyWorkspace.Other
    }
    val importWorkspaceName = (devices.source as? com.devuloopers.knet.application.contract.traffic.TrafficSource.Device)?.let { source ->
        devices.devices.firstOrNull { it.device.id == source.id }?.device?.displayName
    } ?: "Other traffic"
    KNetApplicationTheme {
        com.devuloopers.knet.ui.desktop.app.workspace.CaptureImportHost(captureImport, importWorkspace, importWorkspaceName,
            { appState.navigationController.navigate(it) }, modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                titleBarWrapper { DeviceWorkspaceTabs(devices.copy(pendingCounts = pendingCounts), { source -> traffic.selectSource(source); deviceWorkspaces.select(source) },
                    Modifier.fillMaxWidth().padding(start = titleBarInsetStart, end = titleBarInsetEnd), onTitleBarInteractiveBoundsChanged,
                    onClose = { id -> if (!trafficClearing) deviceWorkspaces.requestClose(id) },
                    closeInProgress = trafficClearing) }
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
                        importDialogViewModel = captureImport,
                        destination = destination,
                        onNavigateToDestination = { appState.navigationController.navigate(it) },
                        onClearTraffic = {
                            if (!traffic.uiState.value.isClearingHistory) {
                                deviceWorkspaces.requestClear(traffic.uiState.value.source)?.let { source ->
                                    traffic.clearSource(source, beforeClear = { deviceWorkspaces.retainForClear(source) })
                                }
                            }
                        },
                    )
                }
            }
            devices.clearConfirmation?.let { pending ->
                val connected = devices.devices.any { entry ->
                    entry.device.id == pending.source.id && entry.status != DevicePresenceStatus.OFFLINE
                }
                val keepOpen = connected || pending.hasReconnected
                ConfirmDialog(
                    title = if (keepOpen) "Clear this device's traffic?" else "Clear traffic and close this tab?",
                    message = "Remove all locally saved traffic for ${pending.deviceLabel}, including traffic hidden by filters. " +
                        "Its pairing will remain saved." +
                        if (connected) " The device is connected, so its tab will remain visible."
                        else if (pending.hasReconnected) " The device reconnected, so its tab will remain visible." else "",
                    confirmText = if (keepOpen) "Clear traffic" else "Clear and close",
                    onConfirm = { deviceWorkspaces.confirmClear()?.let { source ->
                        traffic.clearSource(source, beforeClear = { deviceWorkspaces.retainForClear(source) },
                            afterClear = { deviceWorkspaces.finishClose(source) }, onFailure = deviceWorkspaces::closeFailed)
                    } },
                    onDismissRequest = deviceWorkspaces::dismissClear,
                )
            }
            devices.closeError?.let { message ->
                ConfirmDialog(title = "Could not close workspace", message = message,
                    confirmText = "OK", onConfirm = deviceWorkspaces::dismissCloseError,
                    onDismissRequest = deviceWorkspaces::dismissCloseError)
            }
        }
    }
}
