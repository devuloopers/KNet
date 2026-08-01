package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Line layout offset and metrics extraction engine.
 */
internal fun measureLineLayoutOffsets(
    layoutResult: TextLayoutResult,
    density: Density
): Pair<List<Dp>, List<Dp>> {
    val lineCount = layoutResult.lineCount
    val topOffsets = ArrayList<Dp>(lineCount)
    val lineHeights = ArrayList<Dp>(lineCount)

    with(density) {
        for (i in 0 until lineCount) {
            val topPx = layoutResult.getLineTop(i)
            val bottomPx = layoutResult.getLineBottom(i)
            topOffsets.add(topPx.toDp())
            lineHeights.add((bottomPx - topPx).toDp())
        }
    }

    return Pair(topOffsets, lineHeights)
}
