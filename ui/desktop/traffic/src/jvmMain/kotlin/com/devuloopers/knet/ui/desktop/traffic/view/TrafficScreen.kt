package com.devuloopers.knet.ui.desktop.traffic.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.ui.core.components.split.HorizontalSplitPane
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.banner.TrafficErrorBanner
import com.devuloopers.knet.ui.desktop.traffic.filter.TrafficFilterBar
import com.devuloopers.knet.ui.desktop.traffic.filter.TrafficFilterBarActions
import com.devuloopers.knet.ui.desktop.traffic.filter.TrafficFilterBarState
import com.devuloopers.knet.ui.desktop.traffic.inspector.TrafficInspectorPanel
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import com.devuloopers.knet.ui.desktop.traffic.table.TrafficTable
import com.devuloopers.knet.ui.desktop.traffic.table.TrafficTableColumnResizeActions
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbar
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbarActions
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbarState
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel

/**
 * Top-level Live Traffic Workspace Screen composable bound strictly to :ui:core design tokens and parameter objects.
 */
@Composable
fun TrafficScreen(
    viewModel: TrafficViewModel,
    onSendToApiStudio: (NetworkRequestSpec) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val themeColors = KNetTheme.colors
    var inspectorSplitRatio by remember { mutableFloatStateOf(0.65f) }

    val toolbarState =
        remember(
            state.captureState,
            state.engineState,
            state.autoScroll,
            state.localIpAddress,
            state.isClearingHistory,
        ) {
            TrafficToolbarState(
                captureState = state.captureState,
                engineState = state.engineState,
                autoScroll = state.autoScroll,
                localIpAddress = state.localIpAddress,
                isClearingHistory = state.isClearingHistory,
            )
        }

    val toolbarActions = remember(viewModel) {
        TrafficToolbarActions(
            onStartCapture = { viewModel.processIntent(TrafficIntent.StartCapture) },
            onStopCapture = { viewModel.processIntent(TrafficIntent.StopCapture) },
            onClearFeed = { viewModel.processIntent(TrafficIntent.ClearFeed) },
            onAutoScrollToggle = { viewModel.processIntent(TrafficIntent.ToggleAutoScroll) }
        )
    }

    val filterBarState = remember(
        state.searchQuery,
        state.selectedProtocolFilter,
        state.selectedMethodFilter,
        state.selectedStatusFilter,
        state.totalAvailableCount,
        state.httpCount,
        state.httpsCount,
        state.http2Count,
        state.columnVisibility
    ) {
        TrafficFilterBarState(
            searchQuery = state.searchQuery,
            selectedProtocol = state.selectedProtocolFilter,
            selectedMethod = state.selectedMethodFilter,
            selectedStatus = state.selectedStatusFilter,
            totalCount = state.totalAvailableCount,
            httpCount = state.httpCount,
            httpsCount = state.httpsCount,
            http2Count = state.http2Count,
            columnVisibility = state.columnVisibility
        )
    }

    val filterBarActions = remember(viewModel) {
        TrafficFilterBarActions(
            onSearchChange = { viewModel.processIntent(TrafficIntent.Search(it)) },
            onProtocolSelected = { viewModel.processIntent(TrafficIntent.FilterByProtocol(it)) },
            onMethodSelected = { viewModel.processIntent(TrafficIntent.FilterByMethod(it)) },
            onStatusSelected = { viewModel.processIntent(TrafficIntent.FilterByStatus(it)) },
            onToggleColumn = { viewModel.processIntent(TrafficIntent.ToggleColumn(it)) },
            onResetColumnWidths = { viewModel.processIntent(TrafficIntent.ResetColumnWidths) },
        )
    }

    val handleExportToStudio: (String) -> Unit = remember(viewModel, onSendToApiStudio) {
        { transactionId ->
            viewModel.exportToStudioSpec(transactionId) { spec ->
                onSendToApiStudio(spec)
            }
        }
    }

    val columnResizeActions = remember(viewModel) {
        TrafficTableColumnResizeActions(
            onResize = { column, widthDp ->
                viewModel.processIntent(TrafficIntent.ResizeColumn(column, widthDp))
            },
            onResizeFinished = {
                viewModel.processIntent(TrafficIntent.CommitColumnWidths)
            },
            onReset = { column ->
                viewModel.processIntent(TrafficIntent.ResetColumnWidth(column))
            },
        )
    }

    KNetSurface(
        modifier = modifier.fillMaxSize(),
        color = themeColors.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Toolbar (56dp)
            TrafficToolbar(
                state = toolbarState,
                actions = toolbarActions
            )

            // 1.1 Error Banner (Visible when engine error occurs)
            TrafficErrorBanner(
                errorMessage = state.engineErrorMessage ?: state.trafficErrorMessage,
                onDismiss = { viewModel.processIntent(TrafficIntent.DismissEngineError) }
            )

            // 2. Quick Filters Row (40dp)
            TrafficFilterBar(
                state = filterBarState,
                actions = filterBarActions
            )

            // 3. Central Workspace (Table + Right Docked Resizable Inspector Split)
            HorizontalSplitPane(
                splitRatio = inspectorSplitRatio,
                onSplitRatioChange = { inspectorSplitRatio = it },
                firstPane = { paneModifier ->
                    TrafficTable(
                        transactions = state.filteredTransactions,
                        selectedId = state.selectedTransactionId,
                        autoScroll = state.autoScroll,
                        columnVisibility = state.columnVisibility,
                        columnWidths = state.columnWidths,
                        columnResizeActions = columnResizeActions,
                        onSelectTransaction = { viewModel.processIntent(TrafficIntent.SelectTransaction(it)) },
                        formattedVisibleSize = state.formattedVisibleSize,
                        totalAvailableCount = state.totalAvailableCount,
                        onSendToApiStudio = handleExportToStudio,
                        onAddBreakpointRule = viewModel::createBreakpointFromTransaction,
                        activeRules = state.activeBreakpointRules,
                        canLoadMore = state.nextPageCursor != null && !state.isPageLoading,
                        onLoadMore = { viewModel.processIntent(TrafficIntent.LoadNextPage) },
                        modifier = paneModifier
                    )
                },
                secondPane = { paneModifier ->
                    TrafficInspectorPanel(
                        selectedTransaction = state.selectedTransaction,
                        activeTab = state.activeInspectorTab,
                        activeRequestSubTab = state.activeRequestSubTab,
                        activeResponseSubTab = state.activeResponseSubTab,
                        preparedState = state.preparedState,
                        onTabSelected = { viewModel.processIntent(TrafficIntent.SelectInspectorTab(it)) },
                        onRequestSubTabSelected = { viewModel.processIntent(TrafficIntent.SelectRequestSubTab(it)) },
                        onResponseSubTabSelected = { viewModel.processIntent(TrafficIntent.SelectResponseSubTab(it)) },
                        onSendToApiStudio = handleExportToStudio,
                        modifier = paneModifier
                    )
                },
                minSplitRatio = 0.2f,
                maxSplitRatio = 0.85f,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        }
    }
}
