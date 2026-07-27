package com.devuloopers.knet.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.controller.ProxyStateController
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.domain.livetraffic.model.LiveTrafficIntent
import com.devuloopers.knet.domain.livetraffic.model.LiveTrafficUiState
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.ui.livetraffic.view.TrafficFeedWidget
import com.devuloopers.knet.ui.rules.view.RulesConsoleWidget
import com.devuloopers.knet.ui.workspace.model.WorkspaceIntent
import com.devuloopers.knet.ui.workspace.model.WorkspaceUiState
import com.devuloopers.knet.widgets.MiddleInspectorWidget
import com.devuloopers.knet.widgets.NotesTagsWidget
import com.devuloopers.knet.widgets.QuickReplayWidget
import com.devuloopers.knet.widgets.WidgetFrame
import com.devuloopers.knet.widgets.WidgetType

/**
 * Pure 3-column workspace content grid for KNet.
 *
 * Renders Live Traffic Feed (Column 1), Inspector (Column 2), and Tools Panel (Column 3).
 * TopHeader and SystemStatusBar are intentionally excluded — they are rendered persistently
 * by [AppNavDisplay] and shared across all navigation destinations.
 *
 * Consumes [WorkspaceUiState] reactively from [WorkspaceViewModel] and emits [WorkspaceIntent]
 * actions following strict Unidirectional Data Flow (UDF).
 *
 * @param controller Controller providing ViewModels and Proxy status.
 * @param currentTab Currently active navigation tab.
 * @param onTabSelected Callback when navigation tab changes.
 * @param selectedTx Currently selected HTTP transaction model.
 * @param liveTrafficState Current UI state of the live traffic feed.
 * @param modifier Layout modifiers.
 */
@Composable
fun WorkspaceLayout(
    controller: ProxyStateController,
    currentTab: String,
    onTabSelected: (String) -> Unit,
    selectedTx: TransactionUiModel?,
    liveTrafficState: LiveTrafficUiState,
    modifier: Modifier = Modifier
) {
    val workspaceViewModel = controller.workspaceViewModel
    val workspaceUiState by workspaceViewModel.uiState.collectAsState()

    when (val state = workspaceUiState) {
        is WorkspaceUiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(KNetColors.BackgroundDark),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = KNetColors.ActiveBlue)
            }
        }

        is WorkspaceUiState.Success -> {
            val visibleWidgets = state.visibleWidgets
            val trafficFeedWidth = state.trafficFeedWidthDp.dp
            val sidebarWidth = state.sidebarWidthDp.dp
            val bottomTrayHeight = state.bottomTrayHeightDp.dp

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(KNetColors.BackgroundDark)
            ) {
                // Sessions Manager Banner (lazy — only rendered when Sessions tab is active)
                if (currentTab == "Sessions") {
                    com.devuloopers.knet.ui.sessions.view.SessionsBannerWidget(
                        controller = controller,
                        onClose = { onTabSelected("Live Traffic") }
                    )
                }

                // 1. Middle Row: Splitting Column 1 (Left), Column 2 (Middle), and Column 3 (Right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // --- Column 1: Live Traffic List (Left) ---
                    if (visibleWidgets[WidgetType.TRAFFIC_FEED] != false) {
                        WidgetFrame(
                            title = WidgetType.TRAFFIC_FEED.title,
                            onClose = {
                                workspaceViewModel.processIntent(WorkspaceIntent.ToggleWidget(WidgetType.TRAFFIC_FEED))
                            },
                            resizeRight = { delta ->
                                val newWidth = (trafficFeedWidth + delta).value.coerceIn(250f, 800f)
                                workspaceViewModel.processIntent(WorkspaceIntent.UpdateTrafficFeedWidth(newWidth))
                            },
                            modifier = Modifier
                                .width(trafficFeedWidth)
                                .fillMaxHeight()
                        ) {
                            TrafficFeedWidget(
                                state = liveTrafficState,
                                onIntent = { intent ->
                                    controller.liveTrafficViewModel.processIntent(intent)
                                    if (intent is LiveTrafficIntent.SelectTransaction) {
                                        controller.inspectorViewModel.processIntent(
                                            com.devuloopers.knet.domain.inspector.model.InspectorIntent.SelectTransaction(intent.transactionId)
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // --- Column 2: Tab-Driven Inspector (Middle) ---
                    if (visibleWidgets[WidgetType.INSPECTOR] != false) {
                        WidgetFrame(
                            title = WidgetType.INSPECTOR.title,
                            onClose = {
                                workspaceViewModel.processIntent(WorkspaceIntent.ToggleWidget(WidgetType.INSPECTOR))
                            },
                            modifier = Modifier
                                .weight(1.8f)
                                .fillMaxHeight()
                        ) {
                            MiddleInspectorWidget(
                                transaction = selectedTx,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // --- Column 3: Notes & Tags (Right Sidebar) ---
                    val isNotesTagsVisible = visibleWidgets[WidgetType.NOTES_TAGS] == true

                    if (isNotesTagsVisible) {
                        WidgetFrame(
                            title = WidgetType.NOTES_TAGS.title,
                            onClose = {
                                workspaceViewModel.processIntent(WorkspaceIntent.ToggleWidget(WidgetType.NOTES_TAGS))
                            },
                            resizeLeft = { delta ->
                                val newWidth = (sidebarWidth - delta).value.coerceIn(180f, 500f)
                                workspaceViewModel.processIntent(WorkspaceIntent.UpdateSidebarWidth(newWidth))
                            },
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight()
                        ) {
                            NotesTagsWidget(modifier = Modifier.fillMaxSize())
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
                                onClose = {
                                    workspaceViewModel.processIntent(WorkspaceIntent.ToggleWidget(WidgetType.RULES_CONSOLE))
                                },
                                resizeTop = { delta ->
                                    val newHeight = (bottomTrayHeight - delta).value.coerceIn(100f, 400f)
                                    workspaceViewModel.processIntent(WorkspaceIntent.UpdateBottomTrayHeight(newHeight))
                                },
                                modifier = Modifier
                                    .weight(3f)
                                    .fillMaxHeight()
                            ) {
                                val rulesState by controller.rulesViewModel.uiState.collectAsState()
                                RulesConsoleWidget(
                                    state = rulesState,
                                    onIntent = { intent -> controller.rulesViewModel.processIntent(intent) }
                                )
                            }
                        }

                        if (isReplayVisible) {
                            WidgetFrame(
                                title = WidgetType.QUICK_REPLAY.title,
                                onClose = {
                                    workspaceViewModel.processIntent(WorkspaceIntent.ToggleWidget(WidgetType.QUICK_REPLAY))
                                },
                                resizeTop = { delta ->
                                    val newHeight = (bottomTrayHeight - delta).value.coerceIn(100f, 400f)
                                    workspaceViewModel.processIntent(WorkspaceIntent.UpdateBottomTrayHeight(newHeight))
                                },
                                modifier = Modifier
                                    .width(280.dp)
                                    .fillMaxHeight()
                            ) {
                                QuickReplayWidget()
                            }
                        }
                    }
                }

            }
        }
    }
}
