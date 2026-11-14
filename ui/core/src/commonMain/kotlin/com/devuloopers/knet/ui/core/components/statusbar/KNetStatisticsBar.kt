package com.devuloopers.knet.ui.core.components.statusbar

import androidx.compose.foundation.background
import com.devuloopers.knet.ui.core.foundation.border.KNetBorderEdges
import com.devuloopers.knet.ui.core.foundation.border.knetBorder
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Single-row statistics footer with theme-owned height and accessible horizontal overflow.
 * Leading and trailing groups align to opposite edges when space permits; their intrinsic widths remain readable
 * in narrow panes and at large font scales. The scrollbar stays pinned to the visible footer.
 * @param modifier Host-owned placement, without feature-specific minimum widths or resize thresholds.
 * @param borderEdges Standalone frame or horizontal separators for a shared split boundary.
 * @param leading Count or summary content at the starting edge.
 * @param center Optional compact action between count and live statistics; overflow follows the same shared policy.
 * @param trailing Live statistics and totals at the ending edge.
 */
@Composable
fun KNetStatisticsBar(modifier: Modifier = Modifier, borderEdges: KNetBorderEdges = KNetBorderEdges.ALL, leading: @Composable RowScope.() -> Unit,
    center: (@Composable RowScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit) {
    val horizontal = rememberScrollState()
    val spacing = KNetTheme.spacing
    BoxWithConstraints(modifier.fillMaxWidth().height(KNetTheme.dimensions.statisticsBarHeight)
        .background(KNetTheme.colors.background).knetBorder(borderEdges)) {
        val contentWidth = (maxWidth - spacing.lg * 2).coerceAtLeast(0.dp)
        Box(Modifier.fillMaxSize().padding(horizontal = spacing.lg).horizontalScroll(horizontal)) {
            Row(Modifier.widthIn(min = contentWidth).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, content = leading)
                if (center != null) Row(Modifier.padding(start = spacing.lg), verticalAlignment = Alignment.CenterVertically, content = center)
                Row(Modifier.padding(start = spacing.lg), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.lg), content = trailing)
            }
        }
        KNetHorizontalScrollbar(horizontal, Modifier.align(Alignment.BottomStart).fillMaxWidth())
    }
}
