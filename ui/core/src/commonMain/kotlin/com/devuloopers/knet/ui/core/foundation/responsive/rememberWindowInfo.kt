package com.devuloopers.knet.ui.core.foundation.responsive

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Remember window info composable calculation helper.
 */
@Composable
fun rememberWindowInfo(width: Dp, height: Dp): WindowInfo {
    return calculateWindowInfo(width, height)
}
