package com.devuloopers.knet.ui.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KNetDarkColorScheme = darkColorScheme(
    primary = KNetColors.ActiveBlue,
    onPrimary = KNetColors.TextPrimary,
    primaryContainer = KNetColors.SurfaceDark,
    onPrimaryContainer = KNetColors.TextPrimary,
    background = KNetColors.BackgroundDark,
    onBackground = KNetColors.TextPrimary,
    surface = KNetColors.SurfaceDark,
    onSurface = KNetColors.TextPrimary,
    surfaceVariant = KNetColors.FieldDark,
    onSurfaceVariant = KNetColors.TextSecondary,
    outline = KNetColors.BorderDark,
    error = KNetColors.ErrorRed,
    onError = KNetColors.TextPrimary
)

/**
 * Custom MaterialTheme configuration applying KNet's dark mode styling.
 *
 * @param content The composable tree receiving this theme configuration.
 */
@Composable
public fun KNetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KNetDarkColorScheme,
        content = content
    )
}
