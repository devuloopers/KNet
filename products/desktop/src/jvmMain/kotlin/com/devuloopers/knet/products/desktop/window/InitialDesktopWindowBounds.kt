package com.devuloopers.knet.products.desktop.window

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.responsive.initialWindowBounds
import java.awt.GraphicsEnvironment

/** Reads the native usable screen once; AWT user-space coordinates already account for display scaling. */
internal fun initialDesktopWindowBounds(): DpRect {
    val workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    return initialWindowBounds(DpRect(workArea.x.dp, workArea.y.dp,
        (workArea.x + workArea.width).dp, (workArea.y + workArea.height).dp))
}
