package com.devuloopers.knet.ui.core.foundation.shapes

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * Immutable corner shape tokens representing sharp, modern IDE container bounds.
 */
@Immutable
data class Shapes(
    val none: CornerBasedShape = RoundedCornerShape(0.dp),
    val small: CornerBasedShape = RoundedCornerShape(2.dp),
    val medium: CornerBasedShape = RoundedCornerShape(4.dp),
    val large: CornerBasedShape = RoundedCornerShape(6.dp),
    val pill: CornerBasedShape = RoundedCornerShape(50)
)

/**
 * Default Shapes instance.
 */
val KNetShapes: Shapes = Shapes()
