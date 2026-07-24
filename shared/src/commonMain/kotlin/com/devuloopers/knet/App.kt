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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.data.MockData
import com.devuloopers.knet.ui.theme.KNetColors
import com.devuloopers.knet.ui.theme.KNetTheme
import com.devuloopers.knet.ui.widgets.InspectorWidget
import com.devuloopers.knet.ui.widgets.QuickReplayWidget
import com.devuloopers.knet.ui.widgets.RequestBodyWidget
import com.devuloopers.knet.ui.widgets.ResponseBodyWidget
import com.devuloopers.knet.ui.widgets.RequestTreeWidget
import com.devuloopers.knet.ui.widgets.RulesConsoleWidget
import com.devuloopers.knet.ui.widgets.TopHeader
import com.devuloopers.knet.ui.widgets.TrafficFeedWidget
import com.devuloopers.knet.ui.widgets.TransactionOverviewWidget
import com.devuloopers.knet.ui.widgets.WidgetFrame
import com.devuloopers.knet.ui.widgets.WidgetType

/**
 * Main application entry point for the KNet User Interface.
 *
 * Implements a dynamic grid layout coordinator where all visual components are encapsulated
 * as independent widgets. Toggling widget configurations dynamically updates the workspace panels.
 */
@Composable
fun App() {
    KNetTheme {
        var currentTab by remember { mutableStateOf("Live Traffic") }
        var selectedTx by remember { mutableStateOf(MockData.transactions.firstOrNull()) }

        var trafficFeedWidth by remember { mutableStateOf(300.dp) }
        var sidebarWidth by remember { mutableStateOf(260.dp) }
        var bottomTrayHeight by remember { mutableStateOf(180.dp) }
        var treePanelWidth by remember { mutableStateOf(320.dp) }
        var requestBodyHeight by remember { mutableStateOf(240.dp) }

        // Thread-safe map of widget visibility states
        var visibleWidgets by remember {
            mutableStateOf(
                WidgetType.values().associateWith { true }
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
                onToggleWidget = toggleWidget
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
                        resizeRight = { delta -> trafficFeedWidth = (trafficFeedWidth + delta).coerceIn(200.dp, 600.dp) },
                        modifier = Modifier
                            .width(trafficFeedWidth)
                            .fillMaxHeight()
                    ) {
                        TrafficFeedWidget(
                            transactions = MockData.transactions,
                            selectedTransaction = selectedTx,
                            onTransactionSelected = { selectedTx = it }
                        )
                    }
                }

                // --- Column 2: Selected Transaction Overview & Details (Middle) ---
                val isOverviewVisible = visibleWidgets[WidgetType.TRANSACTION_OVERVIEW] == true
                val isTreeVisible = visibleWidgets[WidgetType.REQUEST_TREE] == true
                val isReqBodyVisible = visibleWidgets[WidgetType.REQUEST_BODY] == true
                val isResBodyVisible = visibleWidgets[WidgetType.RESPONSE_BODY] == true

                if (isOverviewVisible || isTreeVisible || isReqBodyVisible || isResBodyVisible) {
                    Column(
                        modifier = Modifier
                            .weight(1.8f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Overview headers
                        if (isOverviewVisible) {
                            TransactionOverviewWidget(
                                transaction = selectedTx,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        // Split pane for Tree vs Payloads
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isTreeVisible) {
                                RequestTreeWidget(
                                    transaction = selectedTx,
                                    resizeRight = { delta -> treePanelWidth = (treePanelWidth + delta).coerceIn(200.dp, 600.dp) },
                                    modifier = if (isReqBodyVisible || isResBodyVisible) {
                                        Modifier.width(treePanelWidth).fillMaxHeight()
                                    } else {
                                        Modifier.weight(1f).fillMaxHeight()
                                    }
                                )
                            }

                            if (isReqBodyVisible || isResBodyVisible) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (isReqBodyVisible) {
                                        RequestBodyWidget(
                                            transaction = selectedTx,
                                            resizeBottom = { delta -> requestBodyHeight = (requestBodyHeight + delta).coerceIn(100.dp, 500.dp) },
                                            modifier = if (isResBodyVisible) {
                                                Modifier.height(requestBodyHeight).fillMaxWidth()
                                            } else {
                                                Modifier.weight(1f).fillMaxWidth()
                                            }
                                        )
                                    }

                                    if (isResBodyVisible) {
                                        ResponseBodyWidget(
                                            transaction = selectedTx,
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Column 3: Timings & Note Details (Right Sidebar) ---
                val isTimingsVisible = visibleWidgets[WidgetType.TIMINGS] == true
                val isNotesVisible = visibleWidgets[WidgetType.NOTES_TAGS] == true

                if (isTimingsVisible || isNotesVisible) {
                    WidgetFrame(
                        title = "Inspector",
                        onClose = {
                            visibleWidgets = visibleWidgets + (WidgetType.TIMINGS to false) + (WidgetType.NOTES_TAGS to false)
                        },
                        resizeLeft = { delta -> sidebarWidth = (sidebarWidth - delta).coerceIn(180.dp, 500.dp) },
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                    ) {
                        InspectorWidget(transaction = selectedTx)
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
                            RulesConsoleWidget(rules = MockData.rules)
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
private fun SystemStatusBar() {
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