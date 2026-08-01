package com.devuloopers.knet.ui.desktop.app.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationHost
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationRail
import com.devuloopers.knet.ui.desktop.app.statusbar.StatusBar
import com.devuloopers.knet.ui.desktop.app.toolbar.Toolbar

/**
 * Desktop application entry point composable wrapping the window theme, layout app chrome, and explicit Navigation Host.
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

    KNetTheme {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(KNetColors.BackgroundDark)
        ) {
            // Persistent Top Toolbar
            Toolbar(
                currentDestination = destination,
                onDestinationSelected = { appState.navigationController.navigate(it) }
            )

            // Middle Navigation and Feature Slot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Left Vertical Navigation Rail
                NavigationRail(
                    currentDestination = destination,
                    onDestinationSelected = { appState.navigationController.navigate(it) }
                )

                // Main Swappable Screen Area
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    NavigationHost(destination = destination)
                }
            }

            // Persistent Footer Status Bar
            StatusBar()
        }
    }
}
