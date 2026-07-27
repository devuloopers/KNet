package com.devuloopers.knet.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devuloopers.knet.controller.ProxyStateController
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.domain.livetraffic.model.LiveTrafficUiState
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.ui.apistudio.view.ApiStudioScreen
import com.devuloopers.knet.ui.sessions.view.SessionsScreen
import com.devuloopers.knet.ui.workspace.WorkspaceLayout
import com.devuloopers.knet.ui.workspace.model.WorkspaceIntent
import com.devuloopers.knet.ui.workspace.model.WorkspaceUiState
import com.devuloopers.knet.widgets.SystemStatusBar
import com.devuloopers.knet.widgets.TopHeader
import com.devuloopers.knet.widgets.WidgetType

/**
 * Controller encapsulating application backstack navigation state and destination rendering.
 * Uses a developer-owned backstack [MutableList] following Navigation 3 design principles.
 */
class AppNavigator(
    val backStack: MutableList<Screen>
) {
    /** Current active screen destination at top of backstack. */
    val currentScreen: Screen
        get() = backStack.lastOrNull() ?: Screen.LiveTraffic

    /** Navigates to a target screen destination. */
    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) {
            backStack.add(screen)
        }
    }

    /** Pops top destination from backstack. */
    fun pop(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            return true
        }
        return false
    }
}

@Composable
fun rememberAppNavigator(initialScreen: Screen = Screen.LiveTraffic): AppNavigator {
    val backStack = remember { listOf<Screen>(initialScreen).toMutableStateList() }
    return remember(backStack) { AppNavigator(backStack) }
}

/**
 * Root NavDisplay composable.
 *
 * Renders [TopHeader] and [SystemStatusBar] as **permanent** chrome that never unmounts,
 * and swaps only the content area ([Modifier.weight]) per the active [Screen] destination.
 * This delivers:
 * - Consistent header UX across all tabs.
 * - Zero unnecessary recomposition of the header on tab switches.
 * - Lazy composition of tab content (unmounted when not active).
 */
@Composable
fun AppNavDisplay(
    navigator: AppNavigator,
    controller: ProxyStateController,
    currentTab: String,
    onTabSelected: (String) -> Unit,
    selectedTx: TransactionUiModel?,
    liveTrafficState: LiveTrafficUiState,
    modifier: Modifier = Modifier
) {
    val workspaceViewModel = controller.workspaceViewModel
    val workspaceUiState by workspaceViewModel.uiState.collectAsState()

    val visibleWidgets: Map<WidgetType, Boolean> =
        (workspaceUiState as? WorkspaceUiState.Success)?.visibleWidgets ?: emptyMap()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
    ) {
        // ── PERSISTENT HEADER (Never unmounts across tab switches) ──
        TopHeader(
            currentTab = currentTab,
            onTabSelected = onTabSelected,
            visibleWidgets = visibleWidgets,
            onToggleWidget = { widget ->
                workspaceViewModel.processIntent(WorkspaceIntent.ToggleWidget(widget))
            },
            isProxyRunning = controller.isProxyRunning.value,
            proxyPort = controller.proxyPort,
            onToggleProxy = { controller.toggleProxy() },
            onTrustCa = { controller.trustRootCertificate() }
        )

        // ── SWAPPABLE CONTENT AREA (Only this area changes per tab) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                currentTab == "Live Traffic" -> {
                    if (workspaceUiState is WorkspaceUiState.Loading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = KNetColors.ActiveBlue)
                        }
                    } else {
                        WorkspaceLayout(
                            controller = controller,
                            currentTab = currentTab,
                            onTabSelected = onTabSelected,
                            selectedTx = selectedTx,
                            liveTrafficState = liveTrafficState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                currentTab == "Sessions" -> {
                    SessionsScreen(
                        controller = controller,
                        onOpenSession = { /* TODO: load session into inspector */ },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                currentTab == "API Studio" || currentTab == "Collections" -> {
                    ApiStudioScreen(
                        controller = controller,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                currentTab == "Rules" -> RulesPlaceholderScreen()
                currentTab == "Certificates" -> CertificatesPlaceholderScreen()
                currentTab == "Settings" -> SettingsPlaceholderScreen()

                else -> {
                    WorkspaceLayout(
                        controller = controller,
                        currentTab = currentTab,
                        onTabSelected = onTabSelected,
                        selectedTx = selectedTx,
                        liveTrafficState = liveTrafficState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ── PERSISTENT STATUS BAR (Always visible at the bottom) ──
        SystemStatusBar()
    }
}
