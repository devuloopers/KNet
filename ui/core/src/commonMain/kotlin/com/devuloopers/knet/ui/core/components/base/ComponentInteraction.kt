package com.devuloopers.knet.ui.core.components.base

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Helper creating a remembered interaction source for primitive components.
 */
@Composable
fun rememberComponentInteractionSource(): MutableInteractionSource {
    return remember { MutableInteractionSource() }
}
