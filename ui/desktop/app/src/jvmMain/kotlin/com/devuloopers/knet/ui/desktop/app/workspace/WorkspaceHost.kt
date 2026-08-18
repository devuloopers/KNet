package com.devuloopers.knet.ui.desktop.app.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.rules.model.matchesTransaction
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.view.ApiStudioScreen
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.CollectionsViewModel
import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.AddEditBreakpointRuleDialog
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.LiveInterceptDrawer
import com.devuloopers.knet.ui.desktop.breakpointmanager.view.BreakpointManagerScreen
import com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel
import com.devuloopers.knet.ui.desktop.certificate.view.CertificateManagerScreen
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel
import com.devuloopers.knet.ui.desktop.traffic.view.TrafficScreen
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * IDE Workspace Host composable routing active destinations to feature workspaces.
 */
@Composable
fun KNetWorkspaceHost(
    destination: DesktopDestination,
    onNavigateToDestination: (DesktopDestination) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val apiStudioViewModel: ApiStudioViewModel = koinViewModel()
    val collectionsViewModel: CollectionsViewModel = koinViewModel()
    val breakpointViewModel: BreakpointManagerViewModel = koinViewModel()
    val breakpointState by breakpointViewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        when (destination) {
            DesktopDestination.Traffic -> {
                val trafficViewModel: TrafficViewModel = koinViewModel()
                val trafficState by trafficViewModel.uiState.collectAsState()

                TrafficScreen(
                    viewModel = trafficViewModel,
                    onSendToApiStudio = { spec ->
                        val newSessionId = apiStudioViewModel.importRequestSpec(spec)
                        collectionsViewModel.createUnsavedDraftSession(
                            id = newSessionId,
                            editorState = apiStudioViewModel.uiState.value.editorState,
                            title = apiStudioViewModel.uiState.value.tabs.find { it.id == newSessionId }?.title
                        )
                        onNavigateToDestination(DesktopDestination.ApiStudio)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (trafficState.isBreakpointDialogVisible) {
                    AddEditBreakpointRuleDialog(
                        rule = trafficState.prefilledBreakpointRule,
                        onDismiss = { trafficViewModel.closeBreakpointDialog() },
                        onSave = { urlPattern, method, phase, enabled, protocolCriteria ->
                            trafficViewModel.saveBreakpointRule(urlPattern, method, phase, enabled, protocolCriteria)
                        }
                    )
                }
            }

            DesktopDestination.ApiStudio -> {
                ApiStudioScreen(
                    viewModel = apiStudioViewModel,
                    collectionsViewModel = collectionsViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            DesktopDestination.Certificate -> {
                val certificateViewModel: CertificateViewModel = koinViewModel()
                CertificateManagerScreen(
                    viewModel = certificateViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            DesktopDestination.Breakpoints -> {
                BreakpointManagerScreen(
                    viewModel = breakpointViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            DesktopDestination.Settings -> {
                val settingsViewModel: com.devuloopers.knet.ui.desktop.settings.viewmodel.SettingsViewModel =
                    koinViewModel()
                com.devuloopers.knet.ui.desktop.settings.view.SettingsScreen(
                    viewModel = settingsViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                PlaceholderWorkspaceScreen(
                    destination = destination,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Live Intercept Side Drawer Overlay
        LiveInterceptDrawer(
            events = breakpointState.activeEvents,
            activeEvent = breakpointState.activeEvent,
            isVisible = breakpointState.activeEvent != null,
            resolvedPayloads = breakpointState.resolvedPayloads,
            onSelectEvent = { eventId ->
                breakpointViewModel.selectActiveEvent(eventId)
            },
            onDropItem = { eventId ->
                breakpointViewModel.dropEvent(eventId)
            },
            onDropAll = {
                breakpointViewModel.dropAllEvents()
            },
            onForwardRequest = { modifiedReq ->
                val event = breakpointState.activeEvent ?: return@LiveInterceptDrawer
                breakpointViewModel.forwardRequest(event.id, modifiedReq)
            },
            onForwardResponse = { modifiedResp ->
                val event = breakpointState.activeEvent ?: return@LiveInterceptDrawer
                breakpointViewModel.forwardResponse(event.id, modifiedResp)
            },
            onDrop = {
                val event = breakpointState.activeEvent ?: return@LiveInterceptDrawer
                breakpointViewModel.dropEvent(event.id)
            },
            onDisableRule = {
                val event = breakpointState.activeEvent ?: return@LiveInterceptDrawer
                breakpointViewModel.disableMatchingRule(event.ruleId)
            },
            onDismiss = {
                breakpointViewModel.dismissCurrentEvent()
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
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
                DesktopDestination.ApiStudio -> Triple(
                    "API Testing Studio",
                    "API request authoring, collections, and environment variables.",
                    Icons.Default.Build
                )

                DesktopDestination.Inspector -> Triple(
                    "Transaction Inspector",
                    "Header, query parameters, timeline, and payload metadata view.",
                    Icons.Default.Info
                )

                DesktopDestination.Certificate -> Triple(
                    "PKI Certificates Manager",
                    "Root certificate generation, trust stores, and CA management.",
                    Icons.Default.Lock
                )

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
            Text(
                text = subtitle,
                style = typography.caption.copy(color = themeColors.textMuted),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
