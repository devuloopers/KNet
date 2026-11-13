package com.devuloopers.knet.ui.desktop.codeeditor.viewport

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.v2.ScrollbarAdapter

/**
 * Maps thumb movements through stable document geometry instead of the visible rows' average height.
 * This remains continuous when a single wrapped row is taller than the entire viewport.
 */
internal class EditorScrollbarAdapter(
    private val listState: LazyListState,
    private val heights: EditorLineHeightIndex,
) : ScrollbarAdapter {
    override val viewportSize: Double get() = listState.layoutInfo.viewportSize.height.toDouble()
    override val contentSize: Double get() = heights.totalHeight.toDouble().coerceAtLeast(viewportSize)
    override val scrollOffset: Double get() {
        val maximum = (contentSize - viewportSize).coerceAtLeast(0.0)
        if (!listState.canScrollForward && listState.canScrollBackward) return maximum
        return (heights.prefixHeight(listState.firstVisibleItemIndex.coerceAtMost(heights.rowCount)) +
            listState.firstVisibleItemScrollOffset).toDouble().coerceIn(0.0, maximum)
    }

    override suspend fun scrollTo(scrollOffset: Double) {
        val maximum = (contentSize - viewportSize).coerceAtLeast(0.0)
        val target = scrollOffset.coerceIn(0.0, maximum)
        if (target == maximum && maximum > 0) {
            // Let LazyColumn clamp against the real final item, including subpixel text rounding.
            listState.scrollToItem(heights.rowCount - 1, Int.MAX_VALUE)
        } else {
            val position = heights.locate(target)
            listState.scrollToItem(position.row, position.offset)
        }
    }
}
