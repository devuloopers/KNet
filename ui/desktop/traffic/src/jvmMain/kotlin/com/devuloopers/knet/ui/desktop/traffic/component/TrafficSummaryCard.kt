package com.devuloopers.knet.ui.desktop.traffic.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficMetrics

/**
 * Summary card header for traffic feed explorer.
 */
@Composable
fun TrafficSummaryCard(
    metrics: TrafficMetrics,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionBadge(sessionName = "Session #1")
            ConnectionIndicator(isConnected = true)
        }
        FeedStatistics(metrics = metrics)
    }
}
