package com.devuloopers.knet.ui.core.foundation.responsive

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.devuloopers.knet.ui.core.foundation.dimensions.Dimensions
import com.devuloopers.knet.ui.core.foundation.dimensions.KNetDimensions

/**
 * Sizes and centers an initial floating window inside the screen's usable logical bounds.
 *
 * @param workArea Screen bounds after excluding native taskbar, dock and menu reservations, in logical dp.
 * @param dimensions Shared startup fraction and readable minimum dimensions.
 * @return Centered initial bounds at the configured screen fraction, enlarged to the shared minimums where
 * possible and capped to the work area on small displays. This policy does not constrain later user resizing.
 * @throws IllegalArgumentException If either usable screen dimension is non-positive.
 */
fun initialWindowBounds(workArea: DpRect, dimensions: Dimensions = KNetDimensions): DpRect {
    require(workArea.width > 0.dp && workArea.height > 0.dp) { "The usable screen area must have positive dimensions." }
    val width = (workArea.width * dimensions.initialWindowScreenFraction)
        .coerceIn(dimensions.minimumWindowWidth.coerceAtMost(workArea.width), workArea.width)
    val height = (workArea.height * dimensions.initialWindowScreenFraction)
        .coerceIn(dimensions.minimumWindowHeight.coerceAtMost(workArea.height), workArea.height)
    val left = workArea.left + (workArea.width - width) / 2
    val top = workArea.top + (workArea.height - height) / 2
    return DpRect(left, top, left + width, top + height)
}
