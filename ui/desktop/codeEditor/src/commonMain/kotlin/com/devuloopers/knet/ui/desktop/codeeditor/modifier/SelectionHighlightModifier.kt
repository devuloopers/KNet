package com.devuloopers.knet.ui.desktop.codeeditor.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.desktop.codeeditor.model.LineSelectionBounds
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * Custom layout modifier that draws selection highlight overlays behind code text.
 *
 * Utilizes Compose's native [TextLayoutResult.getPathForRange] when available for 100%
 * pixel-accurate multi-row word-wrapped selection highlights, with a fallback rectangle calculation.
 *
 * @param bounds Line-level selection bounds containing start/end column indices and line position flags.
 * @param textLayoutResult Pre-computed native [TextLayoutResult] captured from text composable layout pass.
 * @param fontSize Monospace font size for calculating character column widths in fallback mode.
 * @return Receiver [Modifier] appended with the selection drawing behavior.
 */
fun Modifier.selectionHighlight(
    bounds: LineSelectionBounds?,
    textLayoutResult: TextLayoutResult?,
    fontSize: TextUnit
): Modifier = this.then(
    Modifier.drawBehind {
        bounds?.let { selectionBounds ->
            val layout = textLayoutResult
            if (layout != null) {
                val textLen = layout.layoutInput.text.length
                val safeStart = selectionBounds.startCol.coerceIn(0, textLen)
                val safeEnd = selectionBounds.endCol.coerceIn(safeStart, textLen)
                val charWidthPx = fontSize.toPx() * 0.6f

                if (safeStart < safeEnd) {
                    val path = layout.getPathForRange(safeStart, safeEnd)
                    drawPath(path = path, color = EditorColors.SelectionBackground)
                    if (selectionBounds.isStartLine && safeEnd == textLen) {
                        val lineRight = layout.getLineRight(0)
                        drawRect(
                            color = EditorColors.SelectionBackground,
                            topLeft = Offset(lineRight, 0f),
                            size = Size(charWidthPx, size.height)
                        )
                    }
                } else if (selectionBounds.isStartLine && safeStart == textLen) {
                    val lineRight = layout.getLineRight(0)
                    drawRect(
                        color = EditorColors.SelectionBackground,
                        topLeft = Offset(lineRight, 0f),
                        size = Size(charWidthPx, size.height)
                    )
                } else if (selectionBounds.isMiddleLine && textLen == 0) {
                    drawRect(
                        color = EditorColors.SelectionBackground,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, size.height)
                    )
                }
            } else {
                val charWidthPx = fontSize.toPx() * 0.6f
                val xStart = selectionBounds.startCol * charWidthPx
                val xEnd = selectionBounds.endCol * charWidthPx
                val rectWidth = (xEnd - xStart).coerceAtLeast(charWidthPx * 0.5f)
                drawRect(
                    color = EditorColors.SelectionBackground,
                    topLeft = Offset(xStart, 0f),
                    size = Size(rectWidth, size.height)
                )
            }
        }
    }
)

