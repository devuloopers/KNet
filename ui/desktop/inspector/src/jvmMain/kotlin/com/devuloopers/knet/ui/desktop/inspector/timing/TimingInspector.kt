package com.devuloopers.knet.ui.desktop.inspector.timing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Timing Inspector view container.
 */
@Composable
public fun TimingInspector(
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WaterfallChart(durationMs = durationMs)
        TimingBreakdown(totalDurationMs = durationMs)
    }
}
