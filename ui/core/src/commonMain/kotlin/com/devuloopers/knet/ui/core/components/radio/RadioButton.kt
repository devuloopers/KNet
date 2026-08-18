package com.devuloopers.knet.ui.core.components.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
fun KNetRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    val borderColor = if (selected) themeColors.accent else themeColors.border

    Row(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .handCursor(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(themeColors.surfaceVariant)
                .border(1.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(themeColors.accent)
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
