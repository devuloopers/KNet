package com.devuloopers.knet.ui.desktop.traffic.table

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.feedback.EmptyState

/**
 * Empty traffic feed view when no transactions have been captured.
 */
@Composable
public fun EmptyTrafficView(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            title = "No Traffic Captured",
            description = "Start proxy server or generate HTTP requests to view live capture feed."
        )
    }
}
