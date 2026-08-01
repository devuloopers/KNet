package com.devuloopers.knet.ui.desktop.app.statusbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Host composable rendering status bar footer items.
 *
 * @param items List of status items to render.
 * @param modifier Layout modifier.
 */
@Composable
public fun StatusBarHost(
    items: List<StatusItem>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val textColor = item.color ?: KNetColors.TextSecondary
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.label.isNotEmpty()) {
                    Text(
                        text = "${item.label}:",
                        color = KNetColors.TextMuted,
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = item.value,
                    color = textColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}
