package com.devuloopers.knet.ui.core.foundation.interaction

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

/**
 * Encapsulates interactive UI states for hover, focus, pressed, and selected bounds.
 */
@Immutable
public data class InteractionState(
    val isHovered: Boolean = false,
    val isFocused: Boolean = false,
    val isPressed: Boolean = false,
    val isSelected: Boolean = false
)

/**
 * Remembers a default [MutableInteractionSource] instance.
 */
@Composable
public fun rememberKNetInteractionSource(): MutableInteractionSource {
    return remember { MutableInteractionSource() }
}
