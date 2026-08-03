package com.devuloopers.knet.ui.core.components.base

import androidx.compose.runtime.Immutable

/**
 * Common state holder interface for UI components.
 */
@Immutable
public interface ComponentState {
    val enabled: Boolean
    val focused: Boolean
    val hovered: Boolean
    val pressed: Boolean
}
