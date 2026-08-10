package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Reusable surface card container for settings items.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val themeColors = KNetTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(themeColors.surface, RoundedCornerShape(8.dp))
            .border(1.dp, themeColors.border, RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        content()
    }
}
