package com.devuloopers.knet.ui.core.foundation.spacing

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Immutable grid spacing tokens matching the frozen scale: 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64 dp.
 */
@Immutable
public data class Spacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val huge: Dp = 40.dp,
    val massive: Dp = 48.dp,
    val giant: Dp = 64.dp
)

/**
 * Default Spacing instance.
 */
public val KNetSpacing: Spacing = Spacing()
