package com.devuloopers.knet.ui.core.components.base

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Base marker interface for component defaults.
 */
public interface ComponentDefaults

/**
 * Base color state container for components.
 */
@Immutable
public data class ComponentColors(
    val containerColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
    val borderColor: Color = Color.Unspecified,
    val disabledContainerColor: Color = Color.Unspecified,
    val disabledContentColor: Color = Color.Unspecified
)

/**
 * Base metrics container for component sizing.
 */
@Immutable
public data class ComponentMetrics(
    val height: Dp = 28.dp,
    val horizontalPadding: Dp = 8.dp,
    val verticalPadding: Dp = 4.dp,
    val iconSize: Dp = 16.dp
)
