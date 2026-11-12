package com.devuloopers.knet.ui.core.foundation.theme

import androidx.compose.runtime.Composable

/**
 * Production application appearance, fixed to KNet Dark independently of host appearance or inherited palettes.
 * Desktop and Companion roots use this boundary; [KNetTheme] retains explicit palettes for previews and future work.
 * @param content Application content receiving the shared KNet design tokens and Material bridge.
 */
@Composable
fun KNetApplicationTheme(content: @Composable () -> Unit) {
    KNetTheme(themeMode = ThemeMode.Dark, content = content)
}
