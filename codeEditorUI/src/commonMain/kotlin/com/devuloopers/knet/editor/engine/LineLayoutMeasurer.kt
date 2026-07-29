package com.devuloopers.knet.editor.engine

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * Result container for calculated line tops and line heights in Dp.
 */
data class MeasuredLineOffsets(
    val lineTopOffsetsDp: List<Dp>,
    val lineHeightsDp: List<Dp>
)

/**
 * High-performance layout measurer calculating absolute Y-offsets and line heights for gutter alignment.
 *
 * @param layoutResult Compose [TextLayoutResult] from `BasicTextField.onTextLayout`.
 * @param density Current screen [Density].
 * @return [MeasuredLineOffsets] containing pixel-perfect Dp lists for gutter rendering.
 */
fun measureLineLayoutOffsets(
    layoutResult: TextLayoutResult,
    density: Density
): MeasuredLineOffsets {
    val text = layoutResult.layoutInput.text.text
    val logicalLines = text.lines()
    var charOffset = 0
    val measuredTops = ArrayList<Dp>(logicalLines.size)
    val measuredHeights = ArrayList<Dp>(logicalLines.size)

    for (line in logicalLines) {
        val startOffset = charOffset
        val lastCharOffset = if (line.isEmpty()) {
            startOffset
        } else {
            (startOffset + line.length - 1).coerceAtLeast(startOffset)
        }
        charOffset = (startOffset + line.length + 1).coerceAtMost(text.length)

        val startVisualLine = layoutResult.getLineForOffset(startOffset)
        val endVisualLine = layoutResult.getLineForOffset(lastCharOffset)

        val topPx = layoutResult.getLineTop(startVisualLine)
        val bottomPx = layoutResult.getLineBottom(endVisualLine)

        with(density) {
            measuredTops.add(topPx.toDp())
            measuredHeights.add((bottomPx - topPx).toDp())
        }
    }

    return MeasuredLineOffsets(
        lineTopOffsetsDp = measuredTops,
        lineHeightsDp = measuredHeights
    )
}
