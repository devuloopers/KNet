package com.devuloopers.knet.ui.core.foundation.dimensions

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * Budgets a measured control's width using the same pixel rounding as its individual layout parts.
 *
 * Adding padding, icons and gaps in dp before rounding can reserve less space than those parts actually consume
 * at fractional display scales. Each independently laid-out part must therefore be supplied separately.
 *
 * @param contentWidthPx Intrinsic content width already measured in physical pixels, such as a single-line label.
 * @param surroundingWidths Individual horizontal insets, icon widths and gaps consumed alongside the content.
 * @return The exact pixel budget expressed in dp for Compose sizing and animation modifiers.
 */
fun Density.measuredControlWidth(contentWidthPx: Int, surroundingWidths: List<Dp>): Dp =
    (contentWidthPx + surroundingWidths.sumOf { it.roundToPx() }).toDp()
