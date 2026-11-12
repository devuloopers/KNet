package com.devuloopers.knet.ui.core.components.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Shared manager-table overflow: header and rows scroll together; scrollbars stay pinned to the visible viewport.
 * @param listState Virtualized row state used by content and the visible vertical scrollbar.
 * @param modifier Bounded workspace viewport.
 * @param emptyContent Optional empty state pinned inside the viewport rather than the wider scrollable content.
 * @param header Header receiving the same finite width as the rows; its measured height bounds the scrollbar.
 * @param content Lazy rows receiving the remaining height and the same finite, readable-width content modifier.
 */
@Composable
fun KNetTableViewport(listState: LazyListState, modifier: Modifier = Modifier,
    emptyContent: (@Composable () -> Unit)? = null, header: @Composable (Modifier) -> Unit,
    content: @Composable (Modifier) -> Unit) {
    val spacing = KNetTheme.spacing
    val dimensions = KNetTheme.dimensions
    val horizontal = rememberScrollState()
    val verticalOverflow by remember(listState) { derivedStateOf { listState.canScrollBackward || listState.canScrollForward } }
    val horizontalOverflow by remember(horizontal) { derivedStateOf { horizontal.maxValue > 0 } }
    val verticalGutter = if (emptyContent == null && verticalOverflow) dimensions.scrollbarWidth + spacing.sm else 0.dp
    val horizontalGutter = if (horizontalOverflow) dimensions.scrollbarWidth + spacing.sm else 0.dp
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeight = with(LocalDensity.current) { headerHeightPx.toDp() }
    BoxWithConstraints(modifier.clip(KNetTheme.shapes.small).border(dimensions.borderWidth, KNetTheme.colors.border)
        .background(KNetTheme.colors.surface)) {
        val width = maxOf(maxWidth - verticalGutter, dimensions.managerTableMinWidth)
        // Extend the header surface across the scrollbar lane while keeping every column aligned with the rows.
        Box(Modifier.fillMaxWidth().height(headerHeight).background(KNetTheme.colors.panelHeader))
        Box(Modifier.fillMaxSize().padding(end = verticalGutter, bottom = horizontalGutter).horizontalScroll(horizontal)) {
            Column(Modifier.width(width).fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height }) {
                    header(Modifier.fillMaxWidth())
                }
                content(Modifier.fillMaxWidth().weight(1f))
            }
        }
        if (emptyContent != null) Box(Modifier.fillMaxSize().padding(top = headerHeight, bottom = horizontalGutter)
            .padding(spacing.lg), contentAlignment = Alignment.Center) { emptyContent() }
        else Box(Modifier.fillMaxSize().padding(top = headerHeight, bottom = horizontalGutter), contentAlignment = Alignment.CenterEnd) {
            KNetVerticalScrollbar(listState, Modifier.fillMaxHeight().semantics { contentDescription = "Scroll table rows" })
        }
        KNetHorizontalScrollbar(horizontal, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = verticalGutter)
            .semantics { contentDescription = "Scroll table columns" })
    }
}
