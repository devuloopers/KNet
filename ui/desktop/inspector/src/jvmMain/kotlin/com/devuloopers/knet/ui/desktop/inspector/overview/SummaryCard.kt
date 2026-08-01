package com.devuloopers.knet.ui.desktop.inspector.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes
import com.devuloopers.knet.ui.desktop.inspector.model.TransactionOverview

/**
 * Overview summary card displaying method, status code, URL, duration, and payload size.
 */
@Composable
public fun SummaryCard(
    overview: TransactionOverview,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark, KNetShapes.Medium)
            .padding(10.dp)
    ) {
        Text("Summary", color = KNetColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Method: ${overview.method}", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("Status: ${overview.statusCode} ${overview.statusText}", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("Duration: ${overview.totalDurationMs} ms", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("Size: ${overview.responseSizeBytes} B", color = KNetColors.TextSecondary, fontSize = 11.sp)
    }
}
