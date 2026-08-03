package com.devuloopers.knet.ui.desktop.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.devuloopers.knet.ui.core.foundation.elevation.KNetLayers
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Navigation Rail composable coordinating the 64dp fixed visual anchor and unconstrained floating overlay.
 *
 * Maintains a 64dp visual anchor in the layout tree while rendering [NavigationOverlay]
 * in the [KNetLayers.Navigation] layer without parent measurement capping or clipping.
 *
 * @param currentDestination Active target screen.
 * @param onDestinationSelected Callback when a destination is selected.
 * @param modifier Layout modifier.
 * @param navigationState Single source of truth interaction state coordinator.
 */
@Composable
public fun NavigationRail(
    currentDestination: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    modifier: Modifier = Modifier,
    navigationState: NavigationState = rememberNavigationState()
) {
    val themeColors = KNetTheme.colors
    val presentation = rememberNavigationPresentation(navigationState)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .zIndex(KNetLayers.Navigation)
    ) {
        // Base 64dp stationary background anchor box
        Box(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight()
                .background(themeColors.surface)
                .border(BorderStroke(1.dp, themeColors.border))
        )

        // Independent Floating Overlay Navigation Panel (unconstrained up to 220dp)
        NavigationOverlay(
            navigationState = navigationState,
            presentation = presentation,
            currentDestination = currentDestination,
            onDestinationSelected = onDestinationSelected
        )
    }
}
