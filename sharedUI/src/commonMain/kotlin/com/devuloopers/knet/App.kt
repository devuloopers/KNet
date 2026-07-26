package com.devuloopers.knet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.theme.KNetTheme
import com.devuloopers.knet.widgets.QuickReplayWidget
import com.devuloopers.knet.widgets.RequestBodyWidget
import com.devuloopers.knet.widgets.ResponseBodyWidget
import com.devuloopers.knet.widgets.RequestTreeWidget
import com.devuloopers.knet.widgets.MiddleInspectorWidget
import com.devuloopers.knet.widgets.TopHeader
import com.devuloopers.knet.widgets.TimingsWidget
import com.devuloopers.knet.widgets.NotesTagsWidget
import com.devuloopers.knet.widgets.TransactionOverviewWidget
import com.devuloopers.knet.widgets.WidgetFrame
import com.devuloopers.knet.widgets.WidgetType

import com.devuloopers.knet.ui.livetraffic.view.TrafficFeedWidget

/**
 * Main application entry point for the KNet User Interface.
 *
 * Implements a dynamic grid layout coordinator where all visual components are encapsulated
 * as independent widgets. Toggling widget configurations dynamically updates the workspace panels.
 */
@Composable
fun App(controller: com.devuloopers.knet.controller.ProxyStateController) {
    KNetTheme {
        var currentTab by remember { mutableStateOf("Live Traffic") }

        // Hoist liveTrafficState to Column scope so ALL panels react to selection changes.
        val liveTrafficState by controller.liveTrafficViewModel.uiState.collectAsState()

        // Derive selectedTx reactively from the live traffic feed selection.
        // When the user taps a row in TrafficFeedWidget, liveTrafficState updates and
        // selectedItem changes — this automatically propagates to middle column widgets.
        val selectedTx: com.devuloopers.knet.domain.inspector.model.TransactionUiModel? =
            (liveTrafficState as? com.devuloopers.knet.domain.livetraffic.model.LiveTrafficUiState.Success)
                ?.selectedItem
                ?.let { item ->
                    com.devuloopers.knet.domain.inspector.model.TransactionUiModel(
                        id = item.id,
                        method = item.method,
                        host = item.host,
                        path = item.path,
                        status = item.status,
                        statusText = item.statusText,
                        time = item.formattedTime,
                        size = item.formattedSize,
                        dateGroup = item.dateGroup,
                        requestBody = item.requestBody,
                        responseBody = item.responseBody,
                        queryParams = item.queryParams,
                        requestHeaders = item.requestHeaders,
                        responseHeaders = item.responseHeaders,
                        timingDnsMs = 0L,
                        timingTcpMs = 0L,
                        timingTlsMs = 0L,
                        timingTtfbMs = 0L,
                        timingDownloadMs = 0L
                    )
                }

        var trafficFeedWidth by remember { mutableStateOf(600.dp) }
        var sidebarWidth by remember { mutableStateOf(260.dp) }
        var bottomTrayHeight by remember { mutableStateOf(180.dp) }
        var treePanelWidth by remember { mutableStateOf(320.dp) }
        var requestBodyHeight by remember { mutableStateOf(240.dp) }

        // Thread-safe map of widget visibility states.
        // TIMINGS, NOTES_TAGS, INSPECTOR, RULES_CONSOLE, and QUICK_REPLAY are hidden by default
        // for a clean, non-overwhelming workspace. Users can toggle them on via Widget Manager.
        var visibleWidgets by remember {
            mutableStateOf(
                WidgetType.entries.associateWith { type ->
                    when (type) {
                        WidgetType.TIMINGS,
                        WidgetType.NOTES_TAGS,
                        WidgetType.INSPECTOR,
                        WidgetType.RULES_CONSOLE,
                        WidgetType.QUICK_REPLAY -> false
                        else -> true
                    }
                }
            )
        }

        val toggleWidget = { widget: WidgetType ->
            val isVisible = visibleWidgets[widget] ?: true
            visibleWidgets = visibleWidgets + (widget to !isVisible)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KNetColors.BackgroundDark)
        ) {
            // 1. Navigation Header & Widget Manager Toolbar
            TopHeader(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                visibleWidgets = visibleWidgets,
                onToggleWidget = toggleWidget,
                isProxyRunning = controller.isProxyRunning.value,
                proxyPort = controller.proxyPort,
                onToggleProxy = { controller.toggleProxy() },
                onTrustCa = { controller.trustRootCertificate() }
            )

            // 2. Middle Row: Splitting Column 1 (Left), Column 2 (Middle), and Column 3 (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // --- Column 1: Live Traffic List (Left) ---
                if (visibleWidgets[WidgetType.TRAFFIC_FEED] == true) {
                    WidgetFrame(
                        title = WidgetType.TRAFFIC_FEED.title,
                        onClose = { visibleWidgets = visibleWidgets + (WidgetType.TRAFFIC_FEED to false) },
                        resizeRight = { delta -> trafficFeedWidth = (trafficFeedWidth + delta).coerceIn(250.dp, 800.dp) },
                        modifier = Modifier
                            .width(trafficFeedWidth)
                            .fillMaxHeight()
                    ) {
                        com.devuloopers.knet.ui.livetraffic.view.TrafficFeedWidget(
                            state = liveTrafficState,
                            onIntent = { intent ->
                                controller.liveTrafficViewModel.processIntent(intent)
                                if (intent is com.devuloopers.knet.domain.livetraffic.model.LiveTrafficIntent.SelectTransaction) {
                                    controller.inspectorViewModel.processIntent(
                                        com.devuloopers.knet.domain.inspector.model.InspectorIntent.SelectTransaction(intent.transactionId)
                                    )
                                }
                            }
                        )
                    }
                }

                // --- Column 2: Tab-Driven Inspector (Middle) ---
                MiddleInspectorWidget(
                    transaction = selectedTx,
                    modifier = Modifier
                        .weight(1.8f)
                        .fillMaxHeight()
                )

                // --- Column 3: Connection Timings & Notes (Right Sidebar) ---
                // NOTE: The MIDDLE column (Column 2) IS the full inspector panel.
                // Column 3 is a supplementary sidebar showing timing details and notes,
                // NOT a duplicate of the inspector content.
                val isTimingsVisible = visibleWidgets[WidgetType.TIMINGS] != false
                val isNotesTagsVisible = visibleWidgets[WidgetType.NOTES_TAGS] != false

                if (isTimingsVisible || isNotesTagsVisible) {
                    WidgetFrame(
                        title = "Details",
                        onClose = {
                            visibleWidgets = visibleWidgets +
                                (WidgetType.TIMINGS to false) +
                                (WidgetType.NOTES_TAGS to false)
                        },
                        resizeLeft = { delta -> sidebarWidth = (sidebarWidth - delta).coerceIn(180.dp, 500.dp) },
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                    ) {
                        // Column 3 shows supplementary panels — NOT a duplicate of the
                        // middle inspector. TimingsWidget shows DNS/TCP/TLS/TTFB/Download
                        // gauges; NotesTagsWidget provides a scratch-pad for the selected request.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isTimingsVisible) {
                                TimingsWidget(
                                    transaction = selectedTx,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (isNotesTagsVisible) {
                                NotesTagsWidget(
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // 3. Bottom Tray Row: Console Logs & Rules
            val isRulesVisible = visibleWidgets[WidgetType.RULES_CONSOLE] == true
            val isReplayVisible = visibleWidgets[WidgetType.QUICK_REPLAY] == true

            if (isRulesVisible || isReplayVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomTrayHeight)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isRulesVisible) {
                        WidgetFrame(
                            title = WidgetType.RULES_CONSOLE.title,
                            onClose = { visibleWidgets = visibleWidgets + (WidgetType.RULES_CONSOLE to false) },
                            resizeTop = { delta -> bottomTrayHeight = (bottomTrayHeight - delta).coerceIn(100.dp, 400.dp) },
                            modifier = Modifier
                                .weight(3f)
                                .fillMaxHeight()
                        ) {
                            val rulesState by controller.rulesViewModel.uiState.collectAsState()
                            com.devuloopers.knet.ui.rules.view.RulesConsoleWidget(
                                state = rulesState,
                                onIntent = { intent -> controller.rulesViewModel.processIntent(intent) }
                            )
                        }
                    }

                    if (isReplayVisible) {
                        WidgetFrame(
                            title = WidgetType.QUICK_REPLAY.title,
                            onClose = { visibleWidgets = visibleWidgets + (WidgetType.QUICK_REPLAY to false) },
                            resizeTop = { delta -> bottomTrayHeight = (bottomTrayHeight - delta).coerceIn(100.dp, 400.dp) },
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                        ) {
                            QuickReplayWidget()
                        }
                    }
                }
            }

            // 4. System Footer Status Bar
            SystemStatusBar()
        }
    }
}

/**
 * System status bar footer displaying connection indicators, client sessions,
 * proxy uptime, and active network rates.
 */
@Composable
fun SystemStatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(KNetColors.BackgroundDark)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(KNetColors.SuccessGreen, androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Connected",
                color = KNetColors.SuccessGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "1 Client",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Uptime: 00:12:34",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "↓ 1.25 KB/s",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "↑ 2.34 KB/s",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}