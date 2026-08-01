package com.devuloopers.knet.ui.desktop.app.statusbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Status bar footer container composable hosting status items.
 *
 * @param leftItems Status items aligned to the left.
 * @param rightItems Status items aligned to the right.
 * @param modifier Layout modifier.
 */
@Composable
public fun StatusBar(
    leftItems: List<StatusItem> = emptyList(),
    rightItems: List<StatusItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(KNetColors.BackgroundDark)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusBarHost(items = leftItems)
        StatusBarHost(items = rightItems)
    }
}
