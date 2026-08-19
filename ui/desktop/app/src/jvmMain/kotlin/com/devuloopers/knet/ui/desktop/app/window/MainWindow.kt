package com.devuloopers.knet.ui.desktop.app.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode
import com.devuloopers.knet.ui.desktop.app.dialog.KNetDialogHost
import com.devuloopers.knet.ui.desktop.app.host.KNetApplicationScaffold
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationRail
import com.devuloopers.knet.ui.desktop.app.notification.KNetNotificationHost
import com.devuloopers.knet.ui.desktop.app.overlay.KNetOverlayHost
import com.devuloopers.knet.ui.desktop.app.workspace.KNetWorkspaceHost

/**
 * Desktop application entry point composable wrapping the window theme, layout application shell, and feature hosts.
 *
 * @param appState State holder for window title, bounds, and navigation controller.
 * @param modifier Layout modifier.
 */
@Composable
fun MainWindow(
    appState: MainWindowState = rememberMainWindowState(),
    modifier: Modifier = Modifier
) {
    val destination by appState.navigationController.currentDestination.collectAsState()
    KNetTheme(themeMode = ThemeMode.System) {
        KNetApplicationScaffold(
            modifier = modifier,
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
                onNavigateToDestination = { appState.navigationController.navigate(it) }
            )
        }
    }
}
