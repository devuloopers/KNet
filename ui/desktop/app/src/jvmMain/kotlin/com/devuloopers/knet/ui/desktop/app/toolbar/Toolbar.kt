package com.devuloopers.knet.ui.desktop.app.toolbar

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.toolbar.KNetToolbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination

/**
 * Top toolbar container composable displaying application branding and active context title.
 *
 * @param currentDestination The currently active destination screen.
 * @param onDestinationSelected Callback when a destination selection is triggered.
 * @param modifier Layout modifier.
 */
@Composable
fun Toolbar(
    currentDestination: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    KNetToolbar(modifier = modifier) {
        Text(
            text = "KNet — Desktop Proxy Studio",
            style = typography.titleSmall.copy(color = themeColors.textPrimary),
            modifier = Modifier.weight(1f)
        )

        val destinationLabel = when (currentDestination) {
            DesktopDestination.Traffic -> "Live Traffic"
            DesktopDestination.ConnectDevice -> "Connect Device"
            DesktopDestination.Inspector -> "Inspector"
            DesktopDestination.ApiStudio -> "API Studio"
            DesktopDestination.Certificate -> "Certificates Manager"
            DesktopDestination.Breakpoints -> "Intercepts"
            DesktopDestination.Settings -> "Settings"
        }

        Text(
            text = destinationLabel,
            style = typography.labelMedium.copy(color = themeColors.accent)
        )
    }
}
