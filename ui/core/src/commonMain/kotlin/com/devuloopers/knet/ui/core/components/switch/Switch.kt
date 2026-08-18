package com.devuloopers.knet.ui.core.components.switch

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Standardized High-Density KNet Switch component primitive.
 * Enforces hover highlight and ripple clipped strictly to the pill container.
 * Label text is purely passive with zero hover highlight or ripple background.
 */
@Composable
fun KNetSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    val themeColors = KNetTheme.colors
    val shapes = KNetTheme.shapes
    val typography = KNetTheme.typography

    val trackColor by animateColorAsState(if (checked) themeColors.accent else themeColors.surfaceVariant)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pill container - Hover highlight & ripple strictly constrained to the pill
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 18.dp)
                .clip(shapes.pill)
                .background(trackColor)
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .handCursor()
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
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
