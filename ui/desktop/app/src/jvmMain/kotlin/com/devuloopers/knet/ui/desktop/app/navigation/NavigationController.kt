package com.devuloopers.knet.ui.desktop.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller managing active navigation destinations in KNet Desktop App.
 *
 * Exposes a read-only StateFlow emitting the current [DesktopDestination] to the UI.
 *
 * @param initialDestination The default startup screen.
 */
class NavigationController(
    initialDestination: DesktopDestination = DesktopDestination.Traffic
) {
    private val _currentDestination = MutableStateFlow(initialDestination)

    /**
     * Cold flow exposing the current active navigation screen target.
     */
    val currentDestination: StateFlow<DesktopDestination> = _currentDestination.asStateFlow()

    /**
     * Transitions the application to a new navigation destination.
     *
     * @param destination Target screen.
     */
    fun navigate(destination: DesktopDestination) {
        _currentDestination.value = destination
    }
}
