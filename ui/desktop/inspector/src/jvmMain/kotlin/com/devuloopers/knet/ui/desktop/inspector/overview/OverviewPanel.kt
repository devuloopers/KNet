package com.devuloopers.knet.ui.desktop.inspector.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.feedback.EmptyState
import com.devuloopers.knet.ui.desktop.inspector.model.TransactionOverview

/**
 * Overview panel container displaying summary metrics card and host metadata card.
 */
@Composable
public fun OverviewPanel(
    overview: TransactionOverview?,
    modifier: Modifier = Modifier
) {
    if (overview == null) {
        EmptyState(title = "No Transaction Selected", description = "Select a transaction to inspect metrics.")
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard(overview = overview)
        MetadataCard(overview = overview)
    }
}
