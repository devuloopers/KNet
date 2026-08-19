package com.devuloopers.knet.ui.core.foundation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes as MaterialShapes
import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.devuloopers.knet.ui.core.foundation.motion.KNetMotion
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import com.devuloopers.knet.ui.core.foundation.shapes.KNetShapes
import com.devuloopers.knet.ui.core.foundation.shapes.Shapes
import com.devuloopers.knet.ui.core.foundation.spacing.KNetSpacing
import com.devuloopers.knet.ui.core.foundation.spacing.Spacing
import com.devuloopers.knet.ui.core.foundation.typography.KNetTypography
import com.devuloopers.knet.ui.core.foundation.typography.Typography

/**
 * Single entry point for KNet Design System v3 theme configuration.
 * Exposes all design tokens through explicit CompositionLocals.
 *
 * @param themeMode Desired theme mode. [ThemeMode.System] follows the host appearance.
 * @param colors Optional palette override. When omitted, the palette follows [themeMode].
 * @param typography Typography tokens used by KNet and Material components.
 * @param spacing Spacing tokens used by KNet components.
 * @param shapes Shape tokens used by KNet and Material components.
 * @param dimensions Dimension tokens used by KNet components.
 * @param elevation Elevation tokens used by KNet components.
 * @param motion Motion tokens, including reduced-motion support.
 * @param content The composable scope receiving theme providers.
 */
@Composable
fun KNetTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    colors: Colors? = null,
    typography: Typography = KNetTypography,
    spacing: Spacing = KNetSpacing,
    shapes: Shapes = KNetShapes,
    dimensions: Dimensions = KNetDimensions,
    elevation: Elevation = KNetElevation,
    motion: Motion = KNetMotion,
    content: @Composable () -> Unit
) {
    val useDarkPalette = when (themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val resolvedColors = colors ?: if (useDarkPalette) KNetDarkColors else KNetLightColors
    val materialColorScheme = if (useDarkPalette) {
        darkColorScheme(
            primary = resolvedColors.accent,
            onPrimary = resolvedColors.textPrimary,
            background = resolvedColors.background,
            onBackground = resolvedColors.textPrimary,
            surface = resolvedColors.surface,
            onSurface = resolvedColors.textPrimary,
            surfaceVariant = resolvedColors.surfaceVariant,
            onSurfaceVariant = resolvedColors.textSecondary,
            outline = resolvedColors.border,
            error = resolvedColors.semantic.error,
            errorContainer = resolvedColors.semantic.errorContainer
        )
    } else {
        lightColorScheme(
            primary = resolvedColors.accent,
            onPrimary = resolvedColors.textPrimary,
            background = resolvedColors.background,
            onBackground = resolvedColors.textPrimary,
            surface = resolvedColors.surface,
            onSurface = resolvedColors.textPrimary,
            surfaceVariant = resolvedColors.surfaceVariant,
            onSurfaceVariant = resolvedColors.textSecondary,
            outline = resolvedColors.border,
            error = resolvedColors.semantic.error,
            errorContainer = resolvedColors.semantic.errorContainer
        )
    }
    val materialTypography = MaterialTypography(
        displayLarge = typography.display,
        headlineLarge = typography.heading,
        titleLarge = typography.titleLarge,
        titleMedium = typography.titleMedium,
        titleSmall = typography.titleSmall,
        bodyMedium = typography.bodyMedium,
        bodySmall = typography.bodySmall,
        labelMedium = typography.labelMedium,
        labelSmall = typography.labelSmall
    )
    val materialShapes = MaterialShapes(
        extraSmall = shapes.small,
        small = shapes.small,
        medium = shapes.medium,
        large = shapes.large,
        extraLarge = shapes.large
    )

    CompositionLocalProvider(
        LocalColors provides resolvedColors,
        LocalTypography provides typography,
        LocalSpacing provides spacing,
        LocalShapes provides shapes,
        LocalDimensions provides dimensions,
        LocalElevation provides elevation,
        LocalMotion provides motion
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = materialTypography,
            shapes = materialShapes,
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

}
