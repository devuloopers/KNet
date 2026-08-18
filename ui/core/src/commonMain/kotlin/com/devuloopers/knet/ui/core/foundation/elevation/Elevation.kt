package com.devuloopers.knet.ui.core.foundation.elevation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Immutable elevation tokens defining container layering levels.
 *
 * @property level0 Flat surface elevation (0.dp).
 * @property level1 Low surface elevation for cards and tooltips (2.dp).
 * @property level2 Medium surface elevation for popups and dropdown menus (4.dp).
 * @property level3 High surface elevation for modals and dialogs (8.dp).
 */
@Immutable
data class Elevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 2.dp,
    val level2: Dp = 4.dp,
    val level3: Dp = 8.dp
)

/**
 * Default Elevation instance.
 */
val KNetElevation: Elevation = Elevation()
