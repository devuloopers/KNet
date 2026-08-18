package com.devuloopers.knet.ui.core.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
fun FoundationCatalog() {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Colors Palette Tokens", style = typography.titleMedium, color = colors.textPrimary)
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            Box(modifier = Modifier.size(40.dp).background(colors.accent))
            Box(modifier = Modifier.size(40.dp).background(colors.surface))
            Box(modifier = Modifier.size(40.dp).background(colors.surfaceVariant))
            Box(modifier = Modifier.size(40.dp).background(colors.border))
            Box(modifier = Modifier.size(40.dp).background(colors.semantic.success))
            Box(modifier = Modifier.size(40.dp).background(colors.semantic.error))
        }

        Text("Typography Tokens", style = typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(top = 16.dp))
        Text("Title Large - 16sp SemiBold", style = typography.titleLarge, color = colors.textPrimary)
        Text("Title Medium - 14sp SemiBold", style = typography.titleMedium, color = colors.textSecondary)
        Text("Body Medium - 13sp Normal", style = typography.bodyMedium, color = colors.textPrimary)
        Text("Monospace Code Medium - 12sp", style = typography.codeMedium, color = colors.accent)
    }
}
