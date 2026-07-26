package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.theme.KNetColors

/**
 * Displays a breakdown of the connection timing phases (DNS, TCP, TLS, TTFB, Download)
 * for the currently selected transaction.
 *
 * Each timing segment is rendered as a labelled bar gauge whose width is proportional
 * to the total transaction duration, giving the user a quick visual sense of where
 * time is spent in the request lifecycle.
 *
 * @param transaction The selected transaction whose timing data should be displayed.
 *                    When null, a placeholder "no selection" state is shown.
 * @param modifier    Optional [Modifier] for layout sizing and positioning.
 */
@Composable
fun TimingsWidget(
    transaction: TransactionUiModel?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Connection Timings",
            color = KNetColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (transaction == null) {
            Text(
                text = "No transaction selected",
                color = KNetColors.TextSecondary,
                fontSize = 12.sp
            )
            return@Column
        }

        val totalMs = transaction.totalTimeMs.coerceAtLeast(1L)

        data class TimingSegment(val label: String, val ms: Long, val color: Color)

        val segments = listOf(
            TimingSegment("DNS",      transaction.timings.dnsMs,      Color(0xFF4FC3F7)),
            TimingSegment("TCP",      transaction.timings.tcpMs,      Color(0xFF81C784)),
            TimingSegment("TLS",      transaction.timings.tlsMs,      Color(0xFFFFB74D)),
            TimingSegment("TTFB",     transaction.timings.ttfbMs,     Color(0xFFBA68C8)),
            TimingSegment("Download", transaction.timings.downloadMs,  Color(0xFF4DB6AC)),
        )

        segments.forEach { segment ->
            TimingRow(
                label = segment.label,
                ms = segment.ms,
                totalMs = totalMs,
                barColor = segment.color
            )
        }

        // Total duration summary line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Total",
                color = KNetColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${totalMs} ms",
                color = KNetColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Renders a single timing phase row with a proportional bar and label/ms text.
 *
 * @param label    Short phase name (e.g. "DNS", "TCP").
 * @param ms       Duration of this phase in milliseconds.
 * @param totalMs  Total transaction duration used to compute bar width fraction.
 * @param barColor Accent color for the bar.
 */
@Composable
private fun TimingRow(
    label: String,
    ms: Long,
    totalMs: Long,
    barColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = KNetColors.TextSecondary,
                fontSize = 11.sp
            )
            Text(
                text = if (ms > 0) "${ms} ms" else "–",
                color = KNetColors.TextSecondary,
                fontSize = 11.sp
            )
        }

        // Bar container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(KNetColors.SurfaceDark)
        ) {
            val fraction = (ms.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
        }
    }
}
