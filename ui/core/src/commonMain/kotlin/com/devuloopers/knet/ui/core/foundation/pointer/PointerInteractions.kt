package com.devuloopers.knet.ui.core.foundation.pointer

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * Adds hover callbacks on pointer-driven platforms and remains inert on touch-only platforms.
 *
 * @param onEnter Called when a pointer enters the target.
 * @param onMove Called when a pointer moves within the target.
 * @param onExit Called when a pointer leaves the target.
 * @return This modifier with the platform hover behavior applied.
 */
internal expect fun Modifier.platformHoverEvents(
    onEnter: () -> Unit,
    onMove: () -> Unit,
    onExit: () -> Unit,
): Modifier

/**
 * Adds the platform-appropriate context-menu gesture.
 *
 * Desktop uses a secondary-button press, while touch platforms use a long press.
 *
 * @param gestureKey Restarts gesture observation when the caller's action model changes.
 * @param onOpen Called with the gesture position relative to the target.
 * @return This modifier with context-menu gesture handling applied.
 */
internal expect fun Modifier.platformContextMenuGesture(
    gestureKey: Any?,
    onOpen: (IntOffset) -> Unit,
): Modifier
