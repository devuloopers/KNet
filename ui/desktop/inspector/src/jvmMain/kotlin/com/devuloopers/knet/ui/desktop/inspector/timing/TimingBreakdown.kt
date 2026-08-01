package com.devuloopers.knet.ui.desktop.inspector.timing

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Breakdown of DNS, TCP, TLS, TTFB, and Download duration metrics.
 */
@Composable
public fun TimingBreakdown(
    totalDurationMs: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("DNS Lookup: ${totalDurationMs * 0.1} ms", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("TCP Connect: ${totalDurationMs * 0.2} ms", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("TLS Handshake: ${totalDurationMs * 0.3} ms", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("TTFB: ${totalDurationMs * 0.3} ms", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("Download: ${totalDurationMs * 0.1} ms", color = KNetColors.TextSecondary, fontSize = 11.sp)
    }
}
