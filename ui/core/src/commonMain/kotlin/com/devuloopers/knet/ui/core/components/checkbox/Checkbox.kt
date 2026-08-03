package com.devuloopers.knet.ui.core.components.checkbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun KNetCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    val themeColors = KNetTheme.colors
    val shapes = KNetTheme.shapes
    val typography = KNetTheme.typography

    val containerColor = if (checked) themeColors.accent else themeColors.surfaceVariant
    val borderColor = if (checked) themeColors.accent else themeColors.border

    Row(
        modifier = modifier
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .handCursor(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(shapes.small)
                .background(containerColor)
                .border(1.dp, borderColor, shapes.small),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = KNetIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = typography.bodySmall.copy(color = themeColors.textPrimary),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
