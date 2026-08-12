package com.devuloopers.knet.ui.desktop.traffic.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.ui.core.components.split.HorizontalSplitPane
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.filter.TrafficFilterBar
import com.devuloopers.knet.ui.desktop.traffic.filter.TrafficFilterBarActions
import com.devuloopers.knet.ui.desktop.traffic.filter.TrafficFilterBarState
import com.devuloopers.knet.ui.desktop.traffic.inspector.TrafficInspectorPanel
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import com.devuloopers.knet.ui.desktop.traffic.table.TrafficTable
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbar
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbarActions
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbarState
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel

/**
 * Top-level Live Traffic Workspace Screen composable bound strictly to :ui:core design tokens and parameter objects.
 */
@Composable
public fun TrafficScreen(
    viewModel: TrafficViewModel,
    onSendToApiStudio: (NetworkRequestSpec) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val themeColors = KNetTheme.colors

    val toolbarState = remember(state.captureState, state.engineState, state.autoScroll, state.searchQuery, state.localIpAddress) {
        TrafficToolbarState(
            captureState = state.captureState,
            engineState = state.engineState,
            autoScroll = state.autoScroll,
            searchQuery = state.searchQuery,
            localIpAddress = state.localIpAddress
        )
    }

    val toolbarActions = remember(viewModel) {
        TrafficToolbarActions(
            onStartCapture = { viewModel.processIntent(TrafficIntent.StartCapture) },
            onStopCapture = { viewModel.processIntent(TrafficIntent.StopCapture) },
            onClearFeed = { viewModel.processIntent(TrafficIntent.ClearFeed) },
            onSearchChange = { viewModel.processIntent(TrafficIntent.Search(it)) },
            onAutoScrollToggle = { viewModel.processIntent(TrafficIntent.ToggleAutoScroll) }
        )
    }

    val filterBarState = remember(
        state.selectedProtocolFilter,
        state.selectedMethodFilter,
        state.selectedStatusFilter,
        state.transactions.size,
        state.httpCount,
        state.httpsCount,
        state.wsCount,
        state.otherCount,
        state.columnVisibility
    ) {
        TrafficFilterBarState(
            selectedProtocol = state.selectedProtocolFilter,
            selectedMethod = state.selectedMethodFilter,
            selectedStatus = state.selectedStatusFilter,
            totalCount = state.transactions.size,
            httpCount = state.httpCount,
            httpsCount = state.httpsCount,
            wsCount = state.wsCount,
            otherCount = state.otherCount,
            columnVisibility = state.columnVisibility
        )
    }

    val filterBarActions = remember(viewModel) {
        TrafficFilterBarActions(
            onProtocolSelected = { viewModel.processIntent(TrafficIntent.FilterByProtocol(it)) },
            onMethodSelected = { viewModel.processIntent(TrafficIntent.FilterByMethod(it)) },
            onStatusSelected = { viewModel.processIntent(TrafficIntent.FilterByStatus(it)) },
            onToggleColumn = { viewModel.processIntent(TrafficIntent.ToggleColumn(it)) }
        )
    }

    val handleExportToStudio: (String) -> Unit = remember(viewModel, onSendToApiStudio) {
        { transactionId ->
            viewModel.exportToStudioSpec(transactionId) { spec ->
                onSendToApiStudio(spec)
            }
        }
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

            // 2. Quick Filters Row (40dp)
            TrafficFilterBar(
                state = filterBarState,
                actions = filterBarActions
            )

            // 3. Central Workspace (Table + Right Docked Resizable Inspector Split)
            HorizontalSplitPane(
                firstPane = { paneModifier ->
                    TrafficTable(
                        transactions = state.filteredTransactions,
                        selectedId = state.selectedTransactionId,
                        autoScroll = state.autoScroll,
                        columnVisibility = state.columnVisibility,
                        onSelectTransaction = { viewModel.processIntent(TrafficIntent.SelectTransaction(it)) },
                        formattedTotalSize = state.formattedTotalSize,
                        onSendToApiStudio = handleExportToStudio,
                        modifier = paneModifier
                    )
                },
                secondPane = { paneModifier ->
                    TrafficInspectorPanel(
                        selectedTransaction = state.selectedTransaction,
                        activeTab = state.activeInspectorTab,
                        activeRequestSubTab = state.activeRequestSubTab,
                        activeResponseSubTab = state.activeResponseSubTab,
                        previewMode = state.previewFormatMode,
                        preparedState = state.preparedState,
                        onTabSelected = { viewModel.processIntent(TrafficIntent.SelectInspectorTab(it)) },
                        onRequestSubTabSelected = { viewModel.processIntent(TrafficIntent.SelectRequestSubTab(it)) },
                        onResponseSubTabSelected = { viewModel.processIntent(TrafficIntent.SelectResponseSubTab(it)) },
                        onPreviewModeSelected = { viewModel.processIntent(TrafficIntent.SetPreviewFormatMode(it)) },
                        onSendToApiStudio = handleExportToStudio,
                        modifier = paneModifier
                    )
                },
                initialSplitRatio = 0.65f,
                minSplitRatio = 0.2f,
                maxSplitRatio = 0.85f,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        }
    }
}
