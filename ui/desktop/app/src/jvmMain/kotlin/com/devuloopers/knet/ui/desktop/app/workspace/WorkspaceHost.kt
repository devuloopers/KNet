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
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.view.ApiStudioScreen
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.CollectionsViewModel
import com.devuloopers.knet.ui.desktop.apistudio.protocol.ApiStudioWorkspaceContribution
import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.AddEditBreakpointRuleDrawer
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.LiveInterceptDrawerActions
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.LiveInterceptDrawerState
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.LiveInterceptDrawer
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.ProtocolMessageInterceptDrawer
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.ProtocolMessageInterceptDrawerActions
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.ProtocolMessageInterceptDrawerState
import com.devuloopers.knet.ui.desktop.breakpointmanager.view.BreakpointManagerScreen
import com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel
import com.devuloopers.knet.ui.desktop.certificate.view.CertificateManagerScreen
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel
import com.devuloopers.knet.ui.desktop.connectivity.view.ConnectDeviceScreen
import com.devuloopers.knet.ui.desktop.connectivity.viewmodel.ConnectDeviceViewModel
import com.devuloopers.knet.ui.desktop.traffic.view.TrafficScreen
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficInterceptionUiState
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.getKoin

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
    val koin = getKoin()
    val apiStudioProtocolContributions = remember(koin) { koin.getAll<ApiStudioWorkspaceContribution>() }
    val breakpointViewModel: BreakpointManagerViewModel = koinViewModel()
    val breakpointState by breakpointViewModel.uiState.collectAsState()
    val trafficViewModel: TrafficViewModel = koinViewModel()
    val trafficState by trafficViewModel.uiState.collectAsState()
    val drawerEvent = breakpointState.activeEvent?.takeIf { event ->
        trafficState.transactions.any { row ->
            val interception = row.interception as? TrafficInterceptionUiState.Paused
            row.transactionId == event.candidate.exchangeId.value && interception?.pendingId == event.id
        }
    }
    val messageDrawerEvent = breakpointState.activeMessageEvent?.takeIf { event ->
        trafficState.transactions.any { row ->
            val interception = row.interception as? TrafficInterceptionUiState.Paused
            row.transactionId == event.candidate.exchangeId.value && interception?.pendingId == event.id
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (destination) {
            DesktopDestination.Traffic -> {
                TrafficScreen(
                    viewModel = trafficViewModel,
                    onSendToApiStudio = { spec ->
                        apiStudioViewModel.importRequestSpec(spec)
                        onNavigateToDestination(DesktopDestination.ApiStudio)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                AddEditBreakpointRuleDrawer(
                    visible = trafficState.isBreakpointDrawerVisible,
                    rule = trafficState.prefilledBreakpointRule,
                    isEditingExistingRule = false,
                    protocolDefinitions = breakpointState.protocolDefinitions,
                    initialProtocolValues = trafficState.prefilledBreakpointProtocolValues,
                    onDismiss = trafficViewModel::closeBreakpointDrawer,
                    onSave = { urlPattern, method, phase, enabled, protocolId, protocolValues ->
                        breakpointViewModel.saveRule(
                            urlPattern,
                            method,
                            phase,
                            enabled,
                            protocolId,
                            protocolValues,
                        )
                        trafficViewModel.closeBreakpointDrawer()
                    },
                )
            }

            DesktopDestination.ConnectDevice -> {
                val connectDeviceViewModel: ConnectDeviceViewModel = koinViewModel()
                ConnectDeviceScreen(
                    viewModel = connectDeviceViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            DesktopDestination.ApiStudio -> {
                ApiStudioScreen(
                    viewModel = apiStudioViewModel,
                    collectionsViewModel = collectionsViewModel,
                    protocolContributions = apiStudioProtocolContributions,
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
            state = LiveInterceptDrawerState(
                events = breakpointState.activeEvents,
                activeEvent = drawerEvent,
                isVisible = drawerEvent != null && messageDrawerEvent == null,
                resolvedPayloads = breakpointState.resolvedPayloads,
                requestDescriptors = breakpointState.requestDescriptors,
            ),
            actions = LiveInterceptDrawerActions(
                selectEvent = breakpointViewModel::selectActiveEvent,
                dropItem = breakpointViewModel::dropEvent,
                dropAll = breakpointViewModel::dropAllEvents,
                forwardRequest = { modifiedRequest ->
                    breakpointState.activeEvent?.let { event ->
                        breakpointViewModel.forwardRequest(event.id, modifiedRequest)
                    }
                },
                forwardResponse = { modifiedResponse ->
                    breakpointState.activeEvent?.let { event ->
                        breakpointViewModel.forwardResponse(event.id, modifiedResponse)
                    }
                },
                forwardUnchanged = {
                    breakpointState.activeEvent?.let { event ->
                        breakpointViewModel.forwardUnchanged(event.id)
                    }
                },
                drop = {
                    breakpointState.activeEvent?.let { event -> breakpointViewModel.dropEvent(event.id) }
                },
                disableRule = {
                    breakpointState.activeEvent?.let { event ->
                        breakpointViewModel.disableMatchingRule(event.ruleId)
                    }
                },
                dismiss = breakpointViewModel::dismissCurrentEvent,
            ),
            modifier = Modifier.fillMaxSize()
        )

        ProtocolMessageInterceptDrawer(
            state = ProtocolMessageInterceptDrawerState(
                events = breakpointState.activeMessageEvents,
                activeEvent = messageDrawerEvent,
                isVisible = messageDrawerEvent != null,
            ),
            actions = ProtocolMessageInterceptDrawerActions(
                selectEvent = breakpointViewModel::selectActiveMessageEvent,
                continueUnchanged = breakpointViewModel::continueProtocolMessage,
                replaceAndContinue = breakpointViewModel::replaceProtocolMessage,
                dropStream = breakpointViewModel::dropProtocolMessageStream,
                dismiss = breakpointViewModel::dropProtocolMessageStream,
            ),
            modifier = Modifier.fillMaxSize(),
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
