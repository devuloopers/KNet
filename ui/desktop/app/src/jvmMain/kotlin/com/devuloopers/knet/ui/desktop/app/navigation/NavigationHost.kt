package com.devuloopers.knet.ui.desktop.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.view.SettingsScreen
import com.devuloopers.knet.ui.desktop.settings.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Slot-driven NavigationHost composable routing active destinations in the Desktop Application Framework.
 *
 * @param destination Currently active target screen.
 * @param modifier Layout modifier.
 */
@Composable
public fun NavigationHost(
    destination: DesktopDestination,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    when (destination) {
        DesktopDestination.ApiStudio -> {
            com.devuloopers.knet.ui.desktop.apistudio.view.ApiStudioScreen(modifier = modifier)
        }
        DesktopDestination.Settings -> {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            SettingsScreen(viewModel = settingsViewModel, modifier = modifier)
        }
        else -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val label = when (destination) {
                    DesktopDestination.Traffic -> "Live Traffic Feed (Awaiting Feature Migration Phase)"
                    DesktopDestination.Inspector -> "Transaction Inspector (Awaiting Feature Migration Phase)"
                    DesktopDestination.Certificate -> "CA Certificates Manager (Awaiting Feature Migration Phase)"
                    DesktopDestination.ApiStudio -> ""
                }

                Text(
                    text = label,
                    style = typography.titleMedium.copy(color = themeColors.textSecondary)
                )
            }
        }
    }
}
