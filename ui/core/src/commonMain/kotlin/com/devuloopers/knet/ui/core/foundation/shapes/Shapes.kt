package com.devuloopers.knet.ui.core.foundation.shapes

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Immutable corner shape tokens representing sharp, modern IDE container bounds.
 */
@Immutable
public data class Shapes(
    val none: Shape = RoundedCornerShape(0.dp),
    val small: Shape = RoundedCornerShape(2.dp),
    val medium: Shape = RoundedCornerShape(4.dp),
    val large: Shape = RoundedCornerShape(6.dp),
    val pill: Shape = RoundedCornerShape(50)
)

/**
 * Default Shapes instance.
 */
public val KNetShapes: Shapes = Shapes()
