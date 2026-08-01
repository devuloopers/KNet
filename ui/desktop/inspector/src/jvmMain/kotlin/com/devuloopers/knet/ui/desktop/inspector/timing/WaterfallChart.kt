package com.devuloopers.knet.ui.desktop.inspector.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Network execution timing waterfall chart bar.
 */
@Composable
public fun WaterfallChart(
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(KNetColors.ActiveBlue.copy(alpha = 0.2f), KNetShapes.Small)
    )
}
