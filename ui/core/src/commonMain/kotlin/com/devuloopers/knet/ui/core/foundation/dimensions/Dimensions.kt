package com.devuloopers.knet.ui.core.foundation.dimensions

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Immutable dimension tokens defining explicit heights, widths, icon sizes, and responsive layout minimums.
 */
@Immutable
public data class Dimensions(
    val toolbarHeight: Dp = 32.dp,
    val statusBarHeight: Dp = 24.dp,
    val navigationWidth: Dp = 48.dp,
    val sidebarWidth: Dp = 240.dp,
    val tableRowHeight: Dp = 26.dp,
    val buttonHeightCompact: Dp = 24.dp,
    val buttonHeightStandard: Dp = 28.dp,
    val buttonHeightLarge: Dp = 34.dp,
    val inputHeightCompact: Dp = 26.dp,
    val inputHeightStandard: Dp = 30.dp,
    val iconSizeSmall: Dp = 14.dp,
    val iconSizeMedium: Dp = 16.dp,
    val iconSizeLarge: Dp = 20.dp,
    val splitterSize: Dp = 4.dp,
    val scrollbarWidth: Dp = 8.dp,
    val borderWidth: Dp = 1.dp,
    val dividerThickness: Dp = 1.dp,
    // Responsive Layout & Window Sizing Tokens
    val toolbarButtonMinWidth: Dp = 32.dp,
    val searchFieldMinWidth: Dp = 160.dp,
    val inspectorMinWidth: Dp = 320.dp,
    val inspectorMaxWidth: Dp = 500.dp,
    val minimumWindowWidth: Dp = 1024.dp,
    val minimumWindowHeight: Dp = 600.dp
)

/**
 * Default Dimensions instance.
 */
public val KNetDimensions: Dimensions = Dimensions()
