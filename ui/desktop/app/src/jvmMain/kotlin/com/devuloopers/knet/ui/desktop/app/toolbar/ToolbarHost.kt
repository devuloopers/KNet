package com.devuloopers.knet.ui.desktop.app.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Host composable rendering toolbar actions.
 *
 * @param actions List of toolbar actions to render.
 * @param modifier Layout modifier.
 */
@Composable
public fun ToolbarHost(
    actions: List<ToolbarAction>,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            val textColor = action.color ?: themeColors.accent
            Row(
                modifier = Modifier
                    .background(themeColors.surfaceVariant, shapes.small)
                    .clickable(enabled = action.isEnabled) { action.onClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (action.icon != null) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = textColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = action.label,
                    style = typography.labelSmall.copy(color = textColor)
                )
            }
        }
    }
}
