package com.devuloopers.knet.ui.core.components.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.accessibility.AccessibilityDefaults
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * High-density icon button with a minimum 24 dp interaction target and button semantics.
 */
@Composable
fun KNetIconButton(
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
    val colors = KNetTheme.colors
    val effectiveTint = if (enabled) tint else KNetTheme.colors.textMuted
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .sizeIn(
                minWidth = AccessibilityDefaults.MinimumInteractiveSize,
                minHeight = AccessibilityDefaults.MinimumInteractiveSize
            )
            .then(modifier)
            .size(size)
            .clip(shapes.small)
            .background(if (hovered && enabled) colors.interaction.hoverOverlay else Color.Transparent)
            .then(
                if (focused) Modifier.border(1.dp, colors.interaction.focusRing, shapes.small) else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .handCursor(enabled),
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
