package com.devuloopers.knet.ui.desktop.app.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * State holder for Desktop main window dimensions, title, and floating flags.
 */
public class WindowState(
    initialTitle: String = "KNet — Desktop Proxy Studio",
    initialWidth: Dp = 1280.dp,
    initialHeight: Dp = 800.dp
) {
    public var title: String by mutableStateOf(initialTitle)
    public var width: Dp by mutableStateOf(initialWidth)
    public var height: Dp by mutableStateOf(initialHeight)
    public var isMinimized: Boolean by mutableStateOf(false)
    public var isMaximized: Boolean by mutableStateOf(false)
}
