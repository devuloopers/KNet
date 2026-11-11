package com.devuloopers.knet.ui.core.components.menu

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Accessible, theme-owned overflow trigger for infrequent inspector actions.
 * @param items Current actions and their availability.
 * @param contentDescription Accessible trigger name describing the action group.
 * @param modifier Trigger layout modifier.
 */
@Composable
fun KNetOverflowMenu(items: List<MenuItem>, contentDescription: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        KNetIconButton(onClick = { expanded = !expanded }, icon = KNetIcons.More,
            contentDescription = contentDescription, tint = KNetTheme.colors.textSecondary)
        DropdownMenu(expanded, onDismissRequest = { expanded = false },
            containerColor = KNetTheme.colors.surfaceVariant, shape = KNetTheme.shapes.small) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item.label, style = KNetTheme.typography.bodySmall.copy(
                    color = if (item.isEnabled) KNetTheme.colors.textPrimary else KNetTheme.colors.textMuted),
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    enabled = item.isEnabled, onClick = { expanded = false; item.onClick() })
            }
        }
    }
}
