package com.devuloopers.knet.ui.desktop.app.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Generic context popup menu primitive component.
 *
 * @param items List of menu items.
 * @param onDismiss Callback when user clicks outside.
 * @param modifier Layout modifier.
 */
@Composable
public fun ContextMenu(
    items: List<MenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    Popup(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .background(themeColors.surface, shapes.medium)
                .border(1.dp, themeColors.border, shapes.medium)
                .padding(vertical = 4.dp)
        ) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = item.isEnabled) {
                            item.onClick()
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        style = typography.bodySmall.copy(color = if (item.isEnabled) themeColors.textPrimary else themeColors.textMuted),
                        modifier = Modifier.weight(1f)
                    )
                    if (item.shortcut != null) {
                        Text(
                            text = item.shortcut,
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    }
                }
            }
        }
    }
}
