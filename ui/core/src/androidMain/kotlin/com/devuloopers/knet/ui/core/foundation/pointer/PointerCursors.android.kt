package com.devuloopers.knet.ui.core.foundation.pointer

import androidx.compose.ui.Modifier

/** Keeps the resize interaction intact on Android, where touch input has no resize cursor. */
actual fun Modifier.resizeHorizontalCursor(): Modifier = this

/** Keeps the resize interaction intact on Android, where touch input has no resize cursor. */
actual fun Modifier.resizeVerticalCursor(): Modifier = this
