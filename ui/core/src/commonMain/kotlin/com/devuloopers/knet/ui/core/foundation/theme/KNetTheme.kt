package com.devuloopers.knet.ui.core.foundation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.devuloopers.knet.ui.core.foundation.color.Colors
import com.devuloopers.knet.ui.core.foundation.color.KNetDarkColors
import com.devuloopers.knet.ui.core.foundation.color.KNetLightColors
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

/**
 * Single entry point for KNet Design System v2.0 theme configuration.
 * Exposes all design tokens through explicit CompositionLocals.
 *
 * @param themeMode Desired theme mode (Dark, Light, System). Defaults to Dark.
 * @param content The composable scope receiving theme providers.
 */
@Composable
fun KNetTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    colors: Colors = if (themeMode == ThemeMode.Light) KNetLightColors else KNetDarkColors,
    typography: Typography = KNetTypography,
    spacing: Spacing = KNetSpacing,
    shapes: Shapes = KNetShapes,
    dimensions: Dimensions = KNetDimensions,
    elevation: Elevation = KNetElevation,
    motion: Motion = KNetMotion,
    icons: KNetIcons = KNetIcons,
    resourceProvider: ResourceProvider = KNetResourceProvider,
    content: @Composable () -> Unit
) {
    val materialColorScheme = if (themeMode == ThemeMode.Light) {
        lightColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.surface,
            outline = colors.border,
            error = colors.semantic.error
        )
    } else {
        darkColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.surface,
            outline = colors.border,
            error = colors.semantic.error
        )
    }

    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        LocalSpacing provides spacing,
        LocalShapes provides shapes,
        LocalDimensions provides dimensions,
        LocalElevation provides elevation,
        LocalMotion provides motion,
        LocalIcons provides icons,
        LocalResources provides resourceProvider
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}

/**
 * Static single object access helper for composable functions consuming KNetTheme properties.
 */
object KNetTheme {
    val colors: Colors
        @Composable
        @ReadOnlyComposable
        get() = LocalColors.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = LocalTypography.current

    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = LocalShapes.current

    val dimensions: Dimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalDimensions.current

    val elevation: Elevation
        @Composable
        @ReadOnlyComposable
        get() = LocalElevation.current

    val motion: Motion
        @Composable
        @ReadOnlyComposable
        get() = LocalMotion.current

    val icons: KNetIcons
        @Composable
        @ReadOnlyComposable
        get() = LocalIcons.current

    val resources: ResourceProvider
        @Composable
        @ReadOnlyComposable
        get() = LocalResources.current
}
