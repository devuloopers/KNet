package com.devuloopers.knet.ui.desktop.app.statusbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Host composable rendering status bar footer items.
 *
 * @param items List of status items to render.
 * @param modifier Layout modifier.
 */
@Composable
fun StatusBarHost(
    items: List<StatusItem>,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val textColor = item.color ?: themeColors.textSecondary
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.label.isNotEmpty()) {
                    Text(
                        text = "${item.label}:",
                        style = typography.caption.copy(color = themeColors.textMuted)
                    )
                }
                Text(
                    text = item.value,
                    style = typography.caption.copy(color = textColor)
                )
            }
        }
    }
}
