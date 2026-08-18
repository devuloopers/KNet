package com.devuloopers.knet.ui.core.components.base

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Common style holder for UI component visual attributes.
 */
@Immutable
data class ComponentStyle(
    val backgroundColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
    val borderColor: Color = Color.Transparent,
    val borderWidth: Dp = Dp.Unspecified
)
