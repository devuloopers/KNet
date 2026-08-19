package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.viewport.EditorVisualLineMap
import kotlin.math.roundToInt

/**
 * Encapsulates the resolved 0-indexed document line index, column index, and display line text for a pointer position.
 *
 * @property documentLineIndex Zero-based logical document line.
 * @property columnIndex Character column offset on the target line.
 * @property displayLineText Visible text rendered on the line (includes `... }` for collapsed fold rows).
 */
internal data class PointerHitResult(
    val documentLineIndex: Int,
    val columnIndex: Int,
    val displayLineText: String
)

/**
 * Single-responsibility engine for mapping viewport pointer coordinates to 2D line and column document offsets.
 *
 * Maps visual list item positions to raw document line numbers using [LazyLine.originalLineIndex] and uses
 * [LazyLine.displayText] bounds so that drag selection across collapsed fold rows highlights the full visible
 * line stub (including `... }`).
 */
internal object PointerHitTestEngine {

    /**
     * Calculates 0-indexed raw document line, column index, and display text corresponding to viewport pointer coordinate [pos].
     *
     * Performs $O(1)$ direct array index mapping from [lazyListState] visual item indices to [visibleLines].
     *
     * @param pos Viewport pointer coordinate offset.
     * @param lazyListState Active [LazyListState] managing visible item offsets.
     * @param visualLineMap Mapping between LazyColumn rows and logical document lines.
     * @param snapshot Immutable source document.
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
        visualLineMap: EditorVisualLineMap,
        snapshot: EditorDocumentSnapshot,
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

        val documentLineIndex: Int
        val columnIndex: Int
        val lineText: String

        if (targetItem != null) {
            val hasVisibleLines = targetItem.index in 0 until visualLineMap.visibleLineCount

            // Map visual LazyColumn item index to actual raw document line index in O(1)
            documentLineIndex = if (hasVisibleLines) {
                visualLineMap.toDocumentLine(targetItem.index)
            } else {
                targetItem.index.coerceIn(0, snapshot.lineCount - 1)
            }

            // Use display text (which includes " ... }" stub for collapsed fold rows) for length bounds
            lineText = if (hasVisibleLines) {
                visualLineMap.lazyLine(snapshot, targetItem.index).displayText
            } else {
                snapshot.line(documentLineIndex)
            }

            val localY = (pos.y - targetItem.offset).coerceAtLeast(0f)
            val localX = (pos.x - gutterWidthPx).coerceAtLeast(0f)

            val layoutResult = lineTextLayoutMap[documentLineIndex]
            if (layoutResult != null) {
                columnIndex = layoutResult.getOffsetForPosition(Offset(localX, localY)).coerceIn(0, lineText.length)
            } else {
                val visualRowIndex = (localY / lineHeightPx).toInt()
                val availableTextWidth = (containerWidthPx - gutterWidthPx).coerceAtLeast(100f)
                val charsPerVisualRow = if (charWidthPx > 0f) {
                    (availableTextWidth / charWidthPx).toInt().coerceAtLeast(10)
                } else 80

                val colInRow = if (charWidthPx > 0f) (localX / charWidthPx).roundToInt() else 0
                columnIndex = (visualRowIndex * charsPerVisualRow + colInRow).coerceIn(0, lineText.length)
            }
        } else {
            val firstVisible = lazyListState.firstVisibleItemIndex
            val firstOffset = lazyListState.firstVisibleItemScrollOffset.toFloat()
            val relativeY = (pos.y + firstOffset).coerceAtLeast(0f)

            val visualItemIdx = if (visualLineMap.visibleLineCount > 0) {
                (firstVisible + (relativeY / lineHeightPx).toInt()).coerceIn(0, visualLineMap.visibleLineCount - 1)
            } else 0

            val hasVisibleLines = visualItemIdx in 0 until visualLineMap.visibleLineCount

            documentLineIndex = if (hasVisibleLines) {
                visualLineMap.toDocumentLine(visualItemIdx)
            } else {
                visualItemIdx.coerceIn(0, snapshot.lineCount - 1)
            }

            lineText = if (hasVisibleLines) {
                visualLineMap.lazyLine(snapshot, visualItemIdx).displayText
            } else {
                snapshot.line(documentLineIndex)
            }

            val localX = (pos.x - gutterWidthPx).coerceAtLeast(0f)
            val layoutResult = lineTextLayoutMap[documentLineIndex]
            columnIndex = layoutResult?.getOffsetForPosition(Offset(localX, 0f))?.coerceIn(0, lineText.length)
                ?: (if (charWidthPx > 0f) (localX / charWidthPx).roundToInt() else 0).coerceIn(0, lineText.length)
        }

        return PointerHitResult(
            documentLineIndex = documentLineIndex,
            columnIndex = columnIndex,
            displayLineText = lineText
        )
    }
}
