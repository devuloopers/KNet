package com.devuloopers.knet.ui.desktop.apistudio.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.badge.StatusBadge
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponsePresentation

/**
 * Summary badge bar for HTTP response status code, execution time, and payload size.
 *
 * @param presentation Response presentation model.
 * @param modifier Layout modifier.
 */
@Composable
public fun ResponseSummary(
    presentation: ResponsePresentation,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusBadge(statusCode = presentation.statusCode)
        Text(
            text = "Time: ${presentation.durationMs} ms",
            color = KNetColors.TextSecondary,
            fontSize = 11.sp
        )
        Text(
            text = "Size: ${presentation.sizeBytes} B",
            color = KNetColors.TextSecondary,
            fontSize = 11.sp
        )
    }
}
