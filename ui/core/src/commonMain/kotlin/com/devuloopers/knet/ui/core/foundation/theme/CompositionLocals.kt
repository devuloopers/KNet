package com.devuloopers.knet.ui.core.foundation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.devuloopers.knet.ui.core.foundation.color.Colors
import com.devuloopers.knet.ui.core.foundation.color.KNetDarkColors
import com.devuloopers.knet.ui.core.foundation.dimensions.Dimensions
import com.devuloopers.knet.ui.core.foundation.dimensions.KNetDimensions
import com.devuloopers.knet.ui.core.foundation.elevation.Elevation
import com.devuloopers.knet.ui.core.foundation.elevation.KNetElevation
import com.devuloopers.knet.ui.core.foundation.motion.KNetMotion
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import com.devuloopers.knet.ui.core.foundation.shapes.KNetShapes
import com.devuloopers.knet.ui.core.foundation.shapes.Shapes
import com.devuloopers.knet.ui.core.foundation.spacing.KNetSpacing
import com.devuloopers.knet.ui.core.foundation.spacing.Spacing
import com.devuloopers.knet.ui.core.foundation.typography.KNetTypography
import com.devuloopers.knet.ui.core.foundation.typography.Typography

internal val LocalColors = staticCompositionLocalOf<Colors> { KNetDarkColors }
internal val LocalTypography = staticCompositionLocalOf<Typography> { KNetTypography }
internal val LocalSpacing = staticCompositionLocalOf<Spacing> { KNetSpacing }
internal val LocalShapes = staticCompositionLocalOf<Shapes> { KNetShapes }
internal val LocalDimensions = staticCompositionLocalOf<Dimensions> { KNetDimensions }
internal val LocalElevation = staticCompositionLocalOf<Elevation> { KNetElevation }
internal val LocalMotion = staticCompositionLocalOf<Motion> { KNetMotion }
