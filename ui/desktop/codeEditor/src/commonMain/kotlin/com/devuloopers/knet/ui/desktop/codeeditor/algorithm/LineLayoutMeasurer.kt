package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

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
 * High-performance, allocation-efficient layout measurer calculating absolute Y-offsets and line heights for gutter alignment.
 *
 * Scans newline indices directly without calling `text.lines()` to prevent main-thread GC pressure.
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
    val textLength = text.length
    var lineStart = 0

    val measuredTops = ArrayList<Dp>(64)
    val measuredHeights = ArrayList<Dp>(64)

    while (lineStart <= textLength) {
        val nextNewline = text.indexOf('\n', lineStart)
        val lineEnd = if (nextNewline != -1) nextNewline else textLength
        val lastCharOffset = if (lineEnd > lineStart) lineEnd - 1 else lineStart

        val startVisualLine = layoutResult.getLineForOffset(lineStart)
        val endVisualLine = layoutResult.getLineForOffset(lastCharOffset)

        val topPx = layoutResult.getLineTop(startVisualLine)
        val bottomPx = layoutResult.getLineBottom(endVisualLine)

        with(density) {
            measuredTops.add(topPx.toDp())
            measuredHeights.add((bottomPx - topPx).toDp())
        }

        if (nextNewline == -1) break
        lineStart = nextNewline + 1
    }

    return MeasuredLineOffsets(
        lineTopOffsetsDp = measuredTops,
        lineHeightsDp = measuredHeights
    )
}
