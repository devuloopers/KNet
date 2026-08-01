package com.devuloopers.knet.ui.desktop.traffic.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.traffic.component.TrafficSummaryCard
import com.devuloopers.knet.ui.desktop.traffic.filter.FilterToolbar
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import com.devuloopers.knet.ui.desktop.traffic.table.TrafficTable
import com.devuloopers.knet.ui.desktop.traffic.toolbar.FeedToolbar
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel

/**
 * Top-level real-time Live Traffic Explorer screen composable.
 *
 * @param viewModel TrafficViewModel managing UDF traffic state.
 * @param modifier Layout parameters.
 */
@Composable
public fun TrafficScreen(
    viewModel: TrafficViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
    ) {
        TrafficSummaryCard(metrics = state.metrics)

        FeedToolbar(
            isPaused = state.isPaused,
            autoScroll = state.autoScroll,
            onPauseToggle = {
                if (state.isPaused) viewModel.processIntent(TrafficIntent.ResumeFeed)
                else viewModel.processIntent(TrafficIntent.PauseFeed)
            },
            onClearFeed = { viewModel.processIntent(TrafficIntent.ClearFeed) },
            onAutoScrollToggle = { viewModel.processIntent(TrafficIntent.ToggleAutoScroll) },
            onExport = {}
        )

        FilterToolbar(
            filter = state.filter,
            onMethodChanged = { viewModel.processIntent(TrafficIntent.FilterByMethod(it)) },
            onStatusChanged = { viewModel.processIntent(TrafficIntent.FilterByStatus(it)) },
            onProtocolChanged = { viewModel.processIntent(TrafficIntent.FilterByProtocol(it)) },
            onSearchChanged = { viewModel.processIntent(TrafficIntent.Search(it)) }
        )

        TrafficTable(
            transactions = if (state.filteredTransactions.isNotEmpty()) state.filteredTransactions else state.transactions,
            selectedId = state.selection.primarySelectedId,
            autoScroll = state.autoScroll,
            onSelectTransaction = { viewModel.processIntent(TrafficIntent.SelectTransaction(it)) },
            modifier = Modifier.weight(1f)
        )
    }
}
