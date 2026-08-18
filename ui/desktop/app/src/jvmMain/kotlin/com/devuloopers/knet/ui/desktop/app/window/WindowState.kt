package com.devuloopers.knet.ui.desktop.app.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.dimensions.KNetDimensions

/**
 * State holder for Desktop main window dimensions, title, floating flags, and minimum bounds tokens.
 */
class WindowState(
    initialTitle: String = "KNet — Desktop Proxy Studio",
    initialWidth: Dp = 1280.dp,
    initialHeight: Dp = 800.dp,
    val minWidth: Dp = KNetDimensions.minimumWindowWidth,
    val minHeight: Dp = KNetDimensions.minimumWindowHeight
) {
    var title: String by mutableStateOf(initialTitle)
    var width: Dp by mutableStateOf(initialWidth.coerceAtLeast(minWidth))
    var height: Dp by mutableStateOf(initialHeight.coerceAtLeast(minHeight))
    var isMinimized: Boolean by mutableStateOf(false)
    var isMaximized: Boolean by mutableStateOf(false)
}
