package com.devuloopers.knet.ui.core.foundation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.devuloopers.knet.ui.core.foundation.color.Colors
import com.devuloopers.knet.ui.core.foundation.color.KNetDarkColors
import com.devuloopers.knet.ui.core.foundation.dimensions.Dimensions
import com.devuloopers.knet.ui.core.foundation.dimensions.KNetDimensions
import com.devuloopers.knet.ui.core.foundation.elevation.Elevation
import com.devuloopers.knet.ui.core.foundation.elevation.KNetElevation
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.motion.KNetMotion
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import com.devuloopers.knet.ui.core.foundation.resources.KNetResourceProvider
import com.devuloopers.knet.ui.core.foundation.resources.ResourceProvider
import com.devuloopers.knet.ui.core.foundation.shapes.KNetShapes
import com.devuloopers.knet.ui.core.foundation.shapes.Shapes
import com.devuloopers.knet.ui.core.foundation.spacing.KNetSpacing
import com.devuloopers.knet.ui.core.foundation.spacing.Spacing
import com.devuloopers.knet.ui.core.foundation.typography.KNetTypography
import com.devuloopers.knet.ui.core.foundation.typography.Typography

public val LocalColors = staticCompositionLocalOf<Colors> { KNetDarkColors }
public val LocalTypography = staticCompositionLocalOf<Typography> { KNetTypography }
public val LocalSpacing = staticCompositionLocalOf<Spacing> { KNetSpacing }
public val LocalShapes = staticCompositionLocalOf<Shapes> { KNetShapes }
public val LocalDimensions = staticCompositionLocalOf<Dimensions> { KNetDimensions }
public val LocalElevation = staticCompositionLocalOf<Elevation> { KNetElevation }
public val LocalMotion = staticCompositionLocalOf<Motion> { KNetMotion }
public val LocalIcons = staticCompositionLocalOf<KNetIcons> { KNetIcons }
public val LocalResources = staticCompositionLocalOf<ResourceProvider> { KNetResourceProvider }
