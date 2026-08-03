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
public class WindowState(
    initialTitle: String = "KNet — Desktop Proxy Studio",
    initialWidth: Dp = 1280.dp,
    initialHeight: Dp = 800.dp,
    public val minWidth: Dp = KNetDimensions.minimumWindowWidth,
    public val minHeight: Dp = KNetDimensions.minimumWindowHeight
) {
    public var title: String by mutableStateOf(initialTitle)
    public var width: Dp by mutableStateOf(initialWidth.coerceAtLeast(minWidth))
    public var height: Dp by mutableStateOf(initialHeight.coerceAtLeast(minHeight))
    public var isMinimized: Boolean by mutableStateOf(false)
    public var isMaximized: Boolean by mutableStateOf(false)
}
