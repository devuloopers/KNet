package com.devuloopers.knet.ui.core.components.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Domain-agnostic compact badge/tag primitive.
 */
@Composable
public fun KNetBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = KNetTheme.colors.surfaceVariant,
    contentColor: Color = KNetTheme.colors.textPrimary
) {
    val shapes = KNetTheme.shapes
    val typography = KNetTheme.typography

    Box(
        modifier = modifier
            .clip(shapes.small)
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.labelSmall.copy(color = contentColor),
            maxLines = 1,
            softWrap = false
        )
    }
}
