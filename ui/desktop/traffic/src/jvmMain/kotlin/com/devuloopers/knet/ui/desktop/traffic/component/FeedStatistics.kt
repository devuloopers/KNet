package com.devuloopers.knet.ui.desktop.traffic.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficMetrics

/**
 * Feed statistics counters display (total requests, req/sec, avg latency, errors).
 */
@Composable
fun FeedStatistics(
    metrics: TrafficMetrics,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.padding(horizontal = 8.dp)) {
        Text("Requests: ${metrics.totalRequests} | ", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("Req/sec: ${metrics.requestsPerSecond} | ", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("Avg Latency: ${metrics.averageLatencyMs} ms | ", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("Errors: ${metrics.errorCount}", color = KNetColors.ErrorRed, fontSize = 11.sp)
    }
}
