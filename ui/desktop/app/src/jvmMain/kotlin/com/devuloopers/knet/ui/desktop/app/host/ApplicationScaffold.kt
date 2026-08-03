package com.devuloopers.knet.ui.desktop.app.host

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.devuloopers.knet.ui.core.foundation.elevation.KNetLayers
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Main application scaffold defining independent rendering layers according to KNet Application Architecture.
 *
 * Feature-First Shell Architecture:
 * The shell provides Navigation, Workspace Hosting, Status Bar, and Layer Management.
 * All top bars (App bar & Menu bar) are removed; feature toolbars become the absolute top-most row in active workspace content.
 *
 * Visual Hierarchy:
 * - Base Workspace Content Layer (z = KNetLayers.Workspace)
 * - Navigation Layer (z = KNetLayers.Navigation)
 * - Overlay Layer (z = KNetLayers.Overlay)
 * - Dialog Layer (z = KNetLayers.Dialog)
 * - Notification Layer (z = KNetLayers.Notification)
 */
@Composable
public fun KNetApplicationScaffold(
    modifier: Modifier = Modifier,
    navigationRail: (@Composable () -> Unit)? = null,
    statusBar: (@Composable () -> Unit)? = null,
    dialogHost: (@Composable () -> Unit)? = null,
    overlayHost: (@Composable () -> Unit)? = null,
    notificationHost: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val themeColors = KNetTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Central Workspace Canvas & Navigation Layer Stack
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Layer 1: Workspace Content Layer (z = KNetLayers.Workspace)
                // Permanently reserves 64.dp for the collapsed rail anchor with zero layout shift
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 64.dp)
                        .zIndex(KNetLayers.Workspace)
                ) {
                    content()
                }

                // Layer 2: Navigation Layer (z = KNetLayers.Navigation)
                // Expanded navigation overlay panel floats freely without clipping
                if (navigationRail != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .zIndex(KNetLayers.Navigation)
                    ) {
                        navigationRail()
                    }
                }
            }

            if (statusBar != null) {
                statusBar()
            }
        }

        // Layer 3: Overlay Layer (z = KNetLayers.Overlay)
        if (overlayHost != null) {
            Box(modifier = Modifier.zIndex(KNetLayers.Overlay)) {
                overlayHost()
            }
        }

        // Layer 4: Dialog Layer (z = KNetLayers.Dialog)
        if (dialogHost != null) {
            Box(modifier = Modifier.zIndex(KNetLayers.Dialog)) {
                dialogHost()
            }
        }

        // Layer 5: Notification Layer (z = KNetLayers.Notification)
        if (notificationHost != null) {
            Box(modifier = Modifier.zIndex(KNetLayers.Notification)) {
                notificationHost()
            }
        }
    }
}
