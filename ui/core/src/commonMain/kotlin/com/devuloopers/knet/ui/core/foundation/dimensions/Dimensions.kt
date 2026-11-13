package com.devuloopers.knet.ui.core.foundation.dimensions

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Immutable dimension tokens defining explicit heights, widths, icon sizes, and responsive layout minimums.
 */
@Immutable
data class Dimensions(
    /** Desktop device workspace strip uses one shared height and one-line width budget. */
    val workspaceTabHeight: Dp = 40.dp,
    val workspaceTabMinWidth: Dp = 140.dp,
    val workspaceTabMaxWidth: Dp = 360.dp,
    /** Compact tab controls are vertically centered within the existing workspace strip. */
    val workspaceTabControlHeight: Dp = 32.dp,
    val workspaceTabCornerRadius: Dp = 8.dp,
    val workspaceTabGap: Dp = 6.dp,
    /** Space reserved for native macOS traffic lights when drawing into full-window content. */
    val nativeWindowControlsInset: Dp = 84.dp,
    val toolbarHeight: Dp = 32.dp,
    /** Shared manager headers reserve consistent title/subtitle height before feature content begins. */
    val screenHeaderMinHeight: Dp = 48.dp,
    val statusBarHeight: Dp = 24.dp,
    /** Statistics footers keep one row and expose horizontal overflow without consuming more list height. */
    val statisticsBarHeight: Dp = 48.dp,
    val navigationWidth: Dp = 48.dp,
    val sidebarWidth: Dp = 240.dp,
    val tableRowHeight: Dp = 26.dp,
    /** Readable saved-rule tables retain their columns and use shared horizontal overflow below this width. */
    val managerTableMinWidth: Dp = 1080.dp,
    /** Rows with inline switches and edit/delete controls. */
    val managerTableRowHeight: Dp = 42.dp,
    val buttonHeightCompact: Dp = 24.dp,
    val buttonHeightStandard: Dp = 28.dp,
    val buttonHeightLarge: Dp = 34.dp,
    val buttonHeightTouch: Dp = 48.dp,
    val inputHeightCompact: Dp = 26.dp,
    val inputHeightStandard: Dp = 30.dp,
    val iconSizeSmall: Dp = 14.dp,
    val iconSizeMedium: Dp = 16.dp,
    val iconSizeLarge: Dp = 20.dp,
    val splitterSize: Dp = 4.dp,
    /** Invisible splitter interaction width, independent of the painted divider thickness. */
    val splitterHitTargetSize: Dp = 8.dp,
    val scrollbarWidth: Dp = 8.dp,
    val borderWidth: Dp = 1.dp,
    val dividerThickness: Dp = 1.dp,
    // Responsive Layout & Window Sizing Tokens
    val toolbarButtonMinWidth: Dp = 32.dp,
    val searchFieldMinWidth: Dp = 160.dp,
    val inspectorMinWidth: Dp = 320.dp,
    val inspectorMaxWidth: Dp = 500.dp,
    /** Message navigation and payload retain readable independent panes under shared horizontal overflow. */
    val inspectorMessageListWidth: Dp = 210.dp,
    val inspectorMessageDetailMinWidth: Dp = 300.dp,
    /** Metadata scrolls independently so it cannot displace the selected message payload. */
    val inspectorMessageMetadataMaxHeight: Dp = 180.dp,
    val dialogWidthMedium: Dp = 520.dp,
    val sideDrawerWidth: Dp = 720.dp,
    val expandedSideDrawerWidth: Dp = 880.dp,
    val minimumWindowWidth: Dp = 1024.dp,
    val minimumWindowHeight: Dp = 600.dp
)

/**
 * Default Dimensions instance.
 */
val KNetDimensions: Dimensions = Dimensions()
