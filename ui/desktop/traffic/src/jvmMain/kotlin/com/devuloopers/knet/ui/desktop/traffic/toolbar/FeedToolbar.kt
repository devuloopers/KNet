package com.devuloopers.knet.ui.desktop.traffic.toolbar

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

/**
 * Feed control toolbar hosting pause, clear, export, and autoscroll controls.
 */
@Composable
public fun FeedToolbar(
    isPaused: Boolean,
    autoScroll: Boolean,
    onPauseToggle: () -> Unit,
    onClearFeed: () -> Unit,
    onAutoScrollToggle: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.BackgroundDark)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PauseFeedButton(isPaused = isPaused, onToggle = onPauseToggle)
        ClearFeedButton(onClear = onClearFeed)
        AutoScrollButton(autoScroll = autoScroll, onToggle = onAutoScrollToggle)
        ExportButton(onExport = onExport)
    }
}
