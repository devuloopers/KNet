package com.devuloopers.knet.ui.desktop.app.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationController

/**
 * State holder for the MainWindow, managing selected destination, window layout, and navigation controllers.
 *
 * Keeps the MainWindow composable lightweight and clean.
 *
 * @property navigationController Controller managing the active destination states.
 * @property windowState The underlying dimension state of the window.
 */
class MainWindowState(
    val navigationController: NavigationController = NavigationController(),
    val windowState: WindowState = WindowState()
)

/**
 * Remembers a [MainWindowState] instance across recompositions.
 */
@Composable
fun rememberMainWindowState(
    navigationController: NavigationController = remember { NavigationController() },
    windowState: WindowState = remember { WindowState() }
): MainWindowState {
    return remember { MainWindowState(navigationController, windowState) }
}
