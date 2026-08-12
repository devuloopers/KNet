package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.roundToInt

/**
 * Single-responsibility engine for mapping viewport pointer coordinates to 2D line and column document offsets.
 *
 * Uses Compose's native Skia layout engine ([TextLayoutResult.getOffsetForPosition]) for 100% pixel-accurate
 * hit-testing, falling back to VS Code's 50% midpoint character rounding formula ([roundToInt]) when layout results
 * are unmeasured.
 */
object PointerHitTestEngine {

    /**
     * Calculates 0-indexed line and column indices corresponding to viewport pointer coordinate [pos].
     *
     * @param pos Viewport pointer coordinate offset.
     * @param lazyListState Active [LazyListState] managing visible item offsets.
     * @param rawLines Current list of document text lines.
     * @param lineHeightPx Line height in pixels.
     * @param charWidthPx Character font width in pixels.
     * @param gutterWidthPx Total line number gutter width in pixels.
     * @param containerWidthPx Total viewport width in pixels.
     * @param lineTextLayoutMap Map of measured line indices to Compose [TextLayoutResult] instances.
     * @return Pair of `(lineIndex, colIndex)`.
     */
    fun calculatePointerLineAndCol(
        pos: Offset,
        lazyListState: LazyListState,
        rawLines: List<String>,
        lineHeightPx: Float,
        charWidthPx: Float,
        gutterWidthPx: Float,
        containerWidthPx: Float,
        lineTextLayoutMap: Map<Int, TextLayoutResult>
    ): Pair<Int, Int> {
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val targetItem = visibleItems.find { item ->
            pos.y >= item.offset && pos.y < item.offset + item.size
        }

        val lineIndex: Int
        val colIndex: Int

        if (targetItem != null) {
            lineIndex = targetItem.index.coerceIn(0, rawLines.lastIndex)
            val lineText = rawLines[lineIndex]
            val localY = (pos.y - targetItem.offset).coerceAtLeast(0f)
            val localX = (pos.x - gutterWidthPx).coerceAtLeast(0f)

            val layoutResult = lineTextLayoutMap[lineIndex]
            if (layoutResult != null) {
                // VS Code caretRangeFromPoint equivalent: Compose native Skia font layout query
                colIndex = layoutResult.getOffsetForPosition(Offset(localX, localY)).coerceIn(0, lineText.length)
            } else {
                // VS Code Math.round() 50% character midpoint rounding fallback
                val visualRowIndex = (localY / lineHeightPx).toInt()
                val availableTextWidth = (containerWidthPx - gutterWidthPx).coerceAtLeast(100f)
                val charsPerVisualRow = if (charWidthPx > 0f) {
                    (availableTextWidth / charWidthPx).toInt().coerceAtLeast(10)
                } else 80

                val colInRow = (localX / charWidthPx).roundToInt()
                colIndex = (visualRowIndex * charsPerVisualRow + colInRow).coerceIn(0, lineText.length)
            }
        } else {
            val firstVisible = lazyListState.firstVisibleItemIndex
            val firstOffset = lazyListState.firstVisibleItemScrollOffset.toFloat()
            val relativeY = (pos.y + firstOffset).coerceAtLeast(0f)
            lineIndex = (firstVisible + (relativeY / lineHeightPx).toInt()).coerceIn(0, rawLines.lastIndex)

            val localX = (pos.x - gutterWidthPx).coerceAtLeast(0f)
            val layoutResult = lineTextLayoutMap[lineIndex]
            if (layoutResult != null) {
                colIndex = layoutResult.getOffsetForPosition(Offset(localX, 0f)).coerceIn(0, rawLines[lineIndex].length)
            } else {
                colIndex = (localX / charWidthPx).roundToInt().coerceIn(0, rawLines[lineIndex].length)
            }
        }

        return lineIndex to colIndex
    }
}
