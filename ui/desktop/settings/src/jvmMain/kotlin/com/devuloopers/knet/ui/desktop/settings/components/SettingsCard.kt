package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Reusable surface card container for settings items.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    KNetSurface(
        modifier = modifier.fillMaxWidth(),
        color = KNetTheme.colors.surface,
        shape = KNetTheme.shapes.medium,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
    ) {
        Box(Modifier.fillMaxWidth().padding(20.dp)) { content() }
    }
}
