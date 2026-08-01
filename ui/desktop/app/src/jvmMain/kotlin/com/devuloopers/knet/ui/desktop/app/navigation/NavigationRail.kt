package com.devuloopers.knet.ui.desktop.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Vertical desktop navigation rail composable rendering explicit application destinations.
 *
 * @param currentDestination Currently active [DesktopDestination].
 * @param onDestinationSelected Callback when a destination is selected.
 * @param modifier Layout modifier.
 */
@Composable
public fun NavigationRail(
    currentDestination: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = listOf(
        NavigationDestinationInfo(DesktopDestination.Traffic, "Traffic", Icons.Default.Refresh),
        NavigationDestinationInfo(DesktopDestination.ApiStudio, "API Studio", Icons.Default.Build),
        NavigationDestinationInfo(DesktopDestination.Inspector, "Inspector", Icons.Default.Info),
        NavigationDestinationInfo(DesktopDestination.Workspace, "Workspace", Icons.Default.Folder),
        NavigationDestinationInfo(DesktopDestination.Scripting, "Scripting", Icons.Default.PlayArrow),
        NavigationDestinationInfo(DesktopDestination.Certificate, "Certificate", Icons.Default.Lock),
        NavigationDestinationInfo(DesktopDestination.Settings, "Settings", Icons.Default.Settings)
    )

    Column(
        modifier = modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(KNetColors.BackgroundDark)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        destinations.forEach { info ->
            val isSelected = currentDestination == info.destination
            Column(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isSelected) KNetColors.SelectedRowHighlight else KNetColors.FieldDark,
                        shape = KNetShapes.Medium
                    )
                    .clickable { onDestinationSelected(info.destination) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = info.icon,
                    contentDescription = info.label,
                    tint = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = info.label,
                    color = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextMuted,
                    fontSize = 8.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Data holder for navigation rail destination mappings.
 */
private data class NavigationDestinationInfo(
    val destination: DesktopDestination,
    val label: String,
    val icon: ImageVector
)
