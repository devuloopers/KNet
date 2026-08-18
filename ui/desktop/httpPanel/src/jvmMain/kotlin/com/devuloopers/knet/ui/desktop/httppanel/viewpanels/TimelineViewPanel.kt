package com.devuloopers.knet.ui.desktop.httppanel.viewpanels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Reusable HTTP timeline & waterfall inspection composable rendering network timing breakdown rows
 * (DNS, TCP, TLS, TTFB, Content Download), connection reuse badge, and total roundtrip latency summary.
 *
 * @param timings Canonical exchange timing observations.
 * @param modifier Composable layout modifier.
 */
@Composable
fun TimelineViewPanel(
    timings: ExchangeTimings,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val scrollState = rememberScrollState()

    val dnsMs = timings.dnsMillis ?: 0L
    val tcpMs = timings.connectMillis ?: 0L
    val tlsMs = timings.tlsMillis ?: 0L
    val ttfbMs = timings.firstByteMillis ?: 0L
    val downloadMs = timings.downloadMillis ?: 0L
    val totalMs = timings.totalMillis?.coerceAtLeast(1L) ?: 1L

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surface)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header & Reused Connection Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NETWORK TIMING BREAKDOWN",
                style = typography.caption.copy(
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            )

            if (timings.connectionReused || (dnsMs == 0L && tcpMs == 0L && tlsMs == 0L)) {
                Box(
                    modifier = Modifier
                        .clip(shapes.pill)
                        .background(Color(0xFF313244))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Reused Connection",
                        style = typography.caption.copy(color = Color(0xFFA6ADC8), fontSize = 10.sp)
                    )
                }
            }
        }

        // 2. Waterfall Rows
        TimelineWaterfallRow(
            label = "DNS Resolution",
            durationMs = dnsMs,
            totalMs = totalMs,
            color = Color(0xFF89B4FA)
        )
        TimelineWaterfallRow(
            label = "TCP Connect",
            durationMs = tcpMs,
            totalMs = totalMs,
            color = Color(0xFF89DCEB)
        )
        TimelineWaterfallRow(
            label = "TLS Handshake",
            durationMs = tlsMs,
            totalMs = totalMs,
            color = Color(0xFFA6E3A1)
        )
        TimelineWaterfallRow(
            label = "TTFB (Wait)",
            durationMs = ttfbMs,
            totalMs = totalMs,
            color = Color(0xFFF9E2AF)
        )
        TimelineWaterfallRow(
            label = "Content Download",
            durationMs = downloadMs,
            totalMs = totalMs,
            color = Color(0xFF74C7EC)
        )

        // 3. Total Latency Summary Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total Latency",
                style = typography.bodySmall.copy(
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "$totalMs ms",
                style = typography.bodySmall.copy(
                    color = themeColors.accent,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

/**
 * Single horizontal waterfall progress bar row with timing fraction and duration value.
 */
@Composable
private fun TimelineWaterfallRow(
    label: String,
    durationMs: Long,
    totalMs: Long,
    color: Color
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val fraction = (durationMs.toFloat() / totalMs.toFloat()).coerceIn(0.02f, 1.0f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = typography.bodySmall.copy(color = themeColors.textSecondary),
            modifier = Modifier.widthIn(min = 60.dp, max = 130.dp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 24.dp)
                .height(18.dp)
                .background(Color(0xFF1E1E2E), shape = shapes.small)
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.7f)
                    .fillMaxWidth(fraction)
                    .clip(shapes.small)
                    .background(color)
            )
        }

        Text(
            text = "$durationMs ms",
            style = typography.codeSmall.copy(color = themeColors.textPrimary),
            modifier = Modifier
                .width(64.dp)
                .padding(start = 8.dp),
            maxLines = 1,
            softWrap = false
        )
    }
}
