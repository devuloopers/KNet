package com.devuloopers.knet.ui.core.components.chip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun KNetChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val containerColor = if (selected) themeColors.accent else themeColors.surfaceVariant
    val textColor = if (selected) themeColors.surface else themeColors.textPrimary

    Box(
        modifier = modifier
            .clip(shapes.pill)
            .background(containerColor)
            .clickable(onClick = onClick)
            .handCursor()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.labelSmall.copy(color = textColor),
            maxLines = 1,
            softWrap = false
        )
    }
}
