package com.devuloopers.knet.ui.core.components.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * High-density square/round icon button.
 */
@Composable
public fun KNetIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = KNetTheme.dimensions.buttonHeightStandard,
    iconSize: Dp = KNetTheme.dimensions.iconSizeMedium,
    enabled: Boolean = true,
    tint: Color = KNetTheme.colors.textPrimary
) {
    val shapes = KNetTheme.shapes
    val effectiveTint = if (enabled) tint else KNetTheme.colors.textMuted

    Box(
        modifier = modifier
            .size(size)
            .clip(shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .handCursor(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = effectiveTint
        )
    }
}
