package com.devuloopers.knet.ui.core.foundation.responsive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive window size classification for desktop displays.
 */
enum class WindowSizeClass {
    Compact,   // < 1280dp
    Medium,    // 1280dp - 1919dp
    Expanded   // >= 1920dp
}

/**
 * Window information holder.
 */
@Immutable
data class WindowInfo(
    val widthSizeClass: WindowSizeClass,
    val heightSizeClass: WindowSizeClass,
    val screenWidthDp: Dp,
    val screenHeightDp: Dp
)

/**
 * Calculates current [WindowInfo] given width and height in Dp.
 */
fun calculateWindowInfo(widthDp: Dp, heightDp: Dp): WindowInfo {
    val widthClass = when {
        widthDp < 1280.dp -> WindowSizeClass.Compact
        widthDp < 1920.dp -> WindowSizeClass.Medium
        else -> WindowSizeClass.Expanded
    }
    val heightClass = when {
        heightDp < 720.dp -> WindowSizeClass.Compact
        heightDp < 1080.dp -> WindowSizeClass.Medium
        else -> WindowSizeClass.Expanded
    }
    return WindowInfo(
        widthSizeClass = widthClass,
        heightSizeClass = heightClass,
        screenWidthDp = widthDp,
        screenHeightDp = heightDp
    )
}
