package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.roundToInt

/**
 * Encapsulates the resolved 0-indexed document line index, column index, and display line text for a pointer position.
 *
 * @property rawLineIndex 0-indexed line position in [DocumentBuffer].
 * @property colIndex Character column offset on the target line.
 * @property displayLineText Visible text rendered on the line (includes `... }` for collapsed fold rows).
 */
data class PointerHitResult(
    val rawLineIndex: Int,
    val colIndex: Int,
    val displayLineText: String
)

/**
 * Single-responsibility engine for mapping viewport pointer coordinates to 2D line and column document offsets.
 *
 * Maps visual list item positions to raw document line numbers using [LazyLine.originalLineIndex] and uses
 * [LazyLine.displayText] bounds so that drag selection across collapsed fold rows highlights the full visible
 * line stub (including `... }`).
 */
object PointerHitTestEngine {

    /**
     * Calculates 0-indexed raw document line, column index, and display text corresponding to viewport pointer coordinate [pos].
     *
     * Performs $O(1)$ direct array index mapping from [lazyListState] visual item indices to [visibleLines].
     *
     * @param pos Viewport pointer coordinate offset.
     * @param lazyListState Active [LazyListState] managing visible item offsets.
     * @param visibleLines List of currently visible [LazyLine] rows rendered in the viewport.
     * @param rawLines Complete list of document text lines.
     * @param lineHeightPx Line height in pixels.
     * @param charWidthPx Character font width in pixels.
     * @param gutterWidthPx Total line number gutter width in pixels.
     * @param containerWidthPx Total viewport width in pixels.
     * @param lineTextLayoutMap Map of measured raw line indices to Compose [TextLayoutResult] instances.
     * @return [PointerHitResult] containing raw line index, column index, and display line text.
     */
    fun calculatePointerHit(
        pos: Offset,
        lazyListState: LazyListState,
        visibleLines: List<LazyLine>,
        rawLines: List<String>,
        lineHeightPx: Float,
        charWidthPx: Float,
        gutterWidthPx: Float,
        containerWidthPx: Float,
        lineTextLayoutMap: Map<Int, TextLayoutResult>
    ): PointerHitResult {
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val targetItem = visibleItems.find { item ->
            pos.y >= item.offset && pos.y < item.offset + item.size
        }

        val rawLineIndex: Int
        val colIndex: Int
        val lineText: String

        if (targetItem != null) {
            val hasVisibleLines = visibleLines.isNotEmpty() && targetItem.index in visibleLines.indices

            // Map visual LazyColumn item index to actual raw document line index in O(1)
            rawLineIndex = if (hasVisibleLines) {
                visibleLines[targetItem.index].originalLineIndex
            } else {
                targetItem.index.coerceIn(0, rawLines.lastIndex)
            }

            // Use display text (which includes " ... }" stub for collapsed fold rows) for length bounds
            lineText = if (hasVisibleLines) {
                visibleLines[targetItem.index].displayText
            } else {
                rawLines.getOrElse(rawLineIndex) { "" }
            }

            val localY = (pos.y - targetItem.offset).coerceAtLeast(0f)
            val localX = (pos.x - gutterWidthPx).coerceAtLeast(0f)

            val layoutResult = lineTextLayoutMap[rawLineIndex]
            if (layoutResult != null) {
                colIndex = layoutResult.getOffsetForPosition(Offset(localX, localY)).coerceIn(0, lineText.length)
            } else {
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

            val visualItemIdx = if (visibleLines.isNotEmpty()) {
                (firstVisible + (relativeY / lineHeightPx).toInt()).coerceIn(0, visibleLines.lastIndex)
            } else 0

            val hasVisibleLines = visibleLines.isNotEmpty() && visualItemIdx in visibleLines.indices

            rawLineIndex = if (hasVisibleLines) {
                visibleLines[visualItemIdx].originalLineIndex
            } else {
                visualItemIdx.coerceIn(0, rawLines.lastIndex)
            }

            lineText = if (hasVisibleLines) {
                visibleLines[visualItemIdx].displayText
            } else {
                rawLines.getOrElse(rawLineIndex) { "" }
            }

            val localX = (pos.x - gutterWidthPx).coerceAtLeast(0f)
            val layoutResult = lineTextLayoutMap[rawLineIndex]
            colIndex = layoutResult?.getOffsetForPosition(Offset(localX, 0f))?.coerceIn(0, lineText.length)
                ?: (localX / charWidthPx).roundToInt().coerceIn(0, lineText.length)
        }

        return PointerHitResult(
            rawLineIndex = rawLineIndex,
            colIndex = colIndex,
            displayLineText = lineText
        )
    }
}
