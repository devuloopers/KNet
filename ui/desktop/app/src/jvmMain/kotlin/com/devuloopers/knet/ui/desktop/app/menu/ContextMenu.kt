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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

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
    Popup(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .background(KNetColors.SurfaceDark, KNetShapes.Medium)
                .border(1.dp, KNetColors.BorderDark, KNetShapes.Medium)
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
                        color = if (item.isEnabled) KNetColors.TextPrimary else KNetColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.shortcut != null) {
                        Text(
                            text = item.shortcut,
                            color = KNetColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
