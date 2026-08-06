package com.devuloopers.knet.ui.desktop.app.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import com.devuloopers.knet.ui.desktop.apistudio.view.ApiStudioScreen
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.traffic.view.TrafficScreen
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * IDE Workspace Host composable routing active destinations to feature workspaces.
 */
@Composable
public fun KNetWorkspaceHost(
    destination: DesktopDestination,
    modifier: Modifier = Modifier
) {
    when (destination) {
        DesktopDestination.Traffic -> {
            val trafficViewModel: TrafficViewModel = koinViewModel()
            TrafficScreen(
                viewModel = trafficViewModel,
                modifier = modifier
            )
        }
        DesktopDestination.ApiStudio -> {
            val apiStudioViewModel: ApiStudioViewModel = koinViewModel()
            ApiStudioScreen(
                viewModel = apiStudioViewModel,
                modifier = modifier
            )
        }
        else -> {
            PlaceholderWorkspaceScreen(
                destination = destination,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun PlaceholderWorkspaceScreen(
    destination: DesktopDestination,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surfaceVariant)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(600.dp)
        ) {
            val (title, description, icon) = when (destination) {
                DesktopDestination.ApiStudio -> Triple("API Testing Studio", "API request authoring, collections, and environment variables.", Icons.Default.Build)
                DesktopDestination.Inspector -> Triple("Transaction Inspector", "Header, query parameters, timeline, and payload metadata view.", Icons.Default.Info)
                DesktopDestination.Certificate -> Triple("PKI Certificates Manager", "Root certificate generation, trust stores, and CA management.", Icons.Default.Lock)
                DesktopDestination.Scripting -> Triple("Automation Scripting Console", "JavaScript/Kotlin automation script execution environment.", Icons.Default.PlayArrow)
                DesktopDestination.Settings -> Triple("Application Settings", "Proxy port configuration, upstream proxy chaining, and themes.", Icons.Default.Settings)
                else -> Triple("KNet Workspace", "Developer suite workspace.", Icons.Default.Info)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).padding(end = 8.dp),
                    tint = themeColors.accent
                )
                Text(
                    text = title,
                    style = typography.heading.copy(color = themeColors.textPrimary)
                )
            }

            Text(
                text = description,
                style = typography.bodyMedium.copy(color = themeColors.textSecondary),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 28.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Open Collection",
                    subtitle = "Load local project collection",
                    icon = "📁",
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                QuickActionCard(
                    title = "Import Collection",
                    subtitle = "Postman, OpenAPI, Curl",
                    icon = "↑",
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    KNetSurface(
        modifier = modifier
            .clip(shapes.medium)
            .clickable(onClick = onClick)
            .handCursor(),
        color = themeColors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, themeColors.border),
        shape = shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = icon, style = typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
            Text(text = title, style = typography.titleSmall.copy(color = themeColors.textPrimary))
            Text(text = subtitle, style = typography.caption.copy(color = themeColors.textMuted), modifier = Modifier.padding(top = 2.dp))
        }
    }
}
