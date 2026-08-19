package com.devuloopers.knet.ui.desktop.codeeditor.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.desktop.codeeditor.component.viewport.LineSelectionBounds
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * Custom layout modifier that draws selection highlight overlays behind code text.
 *
 * Uses Compose's native [TextLayoutResult] for horizontal range geometry, then expands each selected
 * segment through its complete visual-line slot. A deterministic monospace fallback is used before layout.
 *
 * @param bounds Line-level selection bounds containing start/end column indices and line position flags.
 * @param textLayoutResult Pre-computed native [TextLayoutResult] captured from text composable layout pass.
 * @param fontSize Monospace font size for calculating character column widths in fallback mode.
 * @return Receiver [Modifier] appended with the selection drawing behavior.
 */
internal fun Modifier.selectionHighlight(
    bounds: LineSelectionBounds?,
    textLayoutResult: TextLayoutResult?,
    fontSize: TextUnit
): Modifier = this.then(
    Modifier.drawBehind {
        bounds?.let { selectionBounds ->
            if (textLayoutResult != null) {
                val textLen = textLayoutResult.layoutInput.text.length
                val safeStart = selectionBounds.startColumn.coerceIn(0, textLen)
                val safeEnd = selectionBounds.endColumn.coerceIn(safeStart, textLen)
                val charWidthPx = fontSize.toPx() * 0.6f

                if (safeStart < safeEnd) {
                    repeat(textLayoutResult.lineCount) { visualLineIndex ->
                        val lineStart = textLayoutResult.getLineStart(visualLineIndex)
                        val lineEnd = textLayoutResult.getLineEnd(visualLineIndex, visibleEnd = false)
                        val segmentStart = maxOf(safeStart, lineStart)
                        val segmentEnd = minOf(safeEnd, lineEnd)
                        if (segmentStart < segmentEnd) {
                            val horizontalBounds = textLayoutResult
                                .getPathForRange(segmentStart, segmentEnd)
                                .getBounds()
                            val verticalBounds = textLayoutResult.selectionVerticalBounds(
                                visualLineIndex,
                                size.height
                            )
                            drawRect(
                                color = EditorColors.SelectionBackground,
                                topLeft = Offset(horizontalBounds.left, verticalBounds.top),
                                size = Size(horizontalBounds.width, verticalBounds.height)
                            )
                        }
                    }
                }
                if (selectionBounds.includesTrailingLineBreak) {
                    val lastVisualLine = (textLayoutResult.lineCount - 1).coerceAtLeast(0)
                    val lineRight = textLayoutResult.getLineRight(lastVisualLine)
                    val verticalBounds = textLayoutResult.selectionVerticalBounds(lastVisualLine, size.height)
                    drawRect(
                        color = EditorColors.SelectionBackground,
                        topLeft = Offset(lineRight, verticalBounds.top),
                        size = Size(charWidthPx, verticalBounds.height)
                    )
                }
            } else {
                val charWidthPx = fontSize.toPx() * 0.6f
                val xStart = selectionBounds.startColumn * charWidthPx
                val xEnd = selectionBounds.endColumn * charWidthPx
                val trailingLineBreakWidth = if (selectionBounds.includesTrailingLineBreak) {
                    charWidthPx
                } else {
                    0f
                }
                val rectWidth = (xEnd - xStart).coerceAtLeast(0f) + trailingLineBreakWidth
                drawRect(
                    color = EditorColors.SelectionBackground,
                    topLeft = Offset(xStart, 0f),
                    size = Size(rectWidth, size.height)
                )
            }
        }
    }
)

/** Vertical paint bounds for one selected visual line inside its complete layout slot. */
internal data class SelectionVerticalBounds(
    val top: Float,
    val bottom: Float
) {
    /** Non-negative height of this selection paint region. */
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

/**
 * Expands text-layout selection bounds through the leading owned by the surrounding visual-line slots.
 *
 * Text retains its original layout and baseline. The first and final slots consume outer leading, while
 * adjacent wrapped lines meet at the midpoint of any internal leading so selection paint has no seams.
 */
internal fun selectionVerticalBounds(
    visualLineIndex: Int,
    visualLineCount: Int,
    lineTop: Float,
    lineBottom: Float,
    previousLineBottom: Float?,
    nextLineTop: Float?,
    contentHeight: Float,
    containerHeight: Float
): SelectionVerticalBounds {
    require(visualLineCount > 0) { "Visual line count must be positive" }
    require(visualLineIndex in 0 until visualLineCount) { "Visual line index is outside the layout" }

    val safeContainerHeight = containerHeight.coerceAtLeast(0f)
    val contentOffset = ((safeContainerHeight - contentHeight) / 2f).coerceAtLeast(0f)
    val top = if (visualLineIndex == 0) {
        0f
    } else {
        contentOffset + midpoint(previousLineBottom ?: lineTop, lineTop)
    }
    val bottom = if (visualLineIndex == visualLineCount - 1) {
        safeContainerHeight
    } else {
        contentOffset + midpoint(lineBottom, nextLineTop ?: lineBottom)
    }

    return SelectionVerticalBounds(
        top = top.coerceIn(0f, safeContainerHeight),
        bottom = bottom.coerceIn(top.coerceAtMost(safeContainerHeight), safeContainerHeight)
    )
}

private fun midpoint(first: Float, second: Float): Float = first + (second - first) / 2f

private fun TextLayoutResult.selectionVerticalBounds(
    visualLineIndex: Int,
    containerHeight: Float
): SelectionVerticalBounds {
    return selectionVerticalBounds(
        visualLineIndex = visualLineIndex,
        visualLineCount = lineCount,
        lineTop = getLineTop(visualLineIndex),
        lineBottom = getLineBottom(visualLineIndex),
        previousLineBottom = visualLineIndex.takeIf { it > 0 }?.let { getLineBottom(it - 1) },
        nextLineTop = visualLineIndex.takeIf { it < lineCount - 1 }?.let { getLineTop(it + 1) },
        contentHeight = size.height.toFloat(),
        containerHeight = containerHeight
    )
}
