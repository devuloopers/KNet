package com.devuloopers.knet.ui.core.components.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Shared manager-table overflow: header and rows scroll together; scrollbars stay pinned to the visible viewport.
 * @param listState Virtualized row state used by content and the visible vertical scrollbar.
 * @param modifier Bounded workspace viewport.
 * @param emptyContent Optional empty state pinned inside the viewport rather than the wider scrollable content.
 * @param content Header and lazy rows receiving one finite, readable-width content modifier.
 */
@Composable
fun KNetTableViewport(listState: LazyListState, modifier: Modifier = Modifier,
    emptyContent: (@Composable () -> Unit)? = null, content: @Composable (Modifier) -> Unit) {
    val spacing = KNetTheme.spacing
    val dimensions = KNetTheme.dimensions
    val horizontal = rememberScrollState()
    BoxWithConstraints(modifier.clip(KNetTheme.shapes.small).border(dimensions.borderWidth, KNetTheme.colors.border)
        .background(KNetTheme.colors.surface)) {
        val width = maxOf(maxWidth - spacing.md, dimensions.managerTableMinWidth)
        Box(Modifier.fillMaxSize().padding(end = spacing.md, bottom = spacing.md).horizontalScroll(horizontal)) {
            content(Modifier.width(width).fillMaxHeight())
        }
        if (emptyContent != null) Box(Modifier.fillMaxSize().padding(top = dimensions.tableRowHeight, bottom = spacing.md)
            .padding(spacing.lg), contentAlignment = Alignment.Center) { emptyContent() }
        else KNetVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        KNetHorizontalScrollbar(horizontal, Modifier.align(Alignment.BottomStart).fillMaxWidth())
    }
}
