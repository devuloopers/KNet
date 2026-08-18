package com.devuloopers.knet.ui.core.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.foundation.responsive.ResponsiveLayout
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
fun ThemeCatalog() {
    val typography = KNetTheme.typography
    val colors = KNetTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Theme Mode Token Resolution Showcase", style = typography.titleLarge, color = colors.textPrimary)
        Text("Background: ${colors.background}", style = typography.bodyMedium, color = colors.textSecondary)
        Text("Surface: ${colors.surface}", style = typography.bodyMedium, color = colors.textSecondary)
        Text("Accent: ${colors.accent}", style = typography.bodyMedium, color = colors.accent)
    }
}

@Composable
fun ResponsiveCatalog() {
    ResponsiveLayout { windowInfo ->
        val typography = KNetTheme.typography
        val colors = KNetTheme.colors

        Column(modifier = Modifier.fillMaxSize()) {
            Text("Responsive Window Matrix Showcase", style = typography.titleLarge, color = colors.textPrimary)
            Text("Width Size Class: ${windowInfo.widthSizeClass}", style = typography.titleMedium, color = colors.accent)
            Text("Height Size Class: ${windowInfo.heightSizeClass}", style = typography.titleMedium, color = colors.accent)
            Text("Dimensions: ${windowInfo.screenWidthDp} x ${windowInfo.screenHeightDp}", style = typography.bodyMedium, color = colors.textSecondary)
        }
    }
}
