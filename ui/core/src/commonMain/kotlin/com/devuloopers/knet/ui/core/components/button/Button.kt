package com.devuloopers.knet.ui.core.components.button

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * High-density IDE button primitive.
 */
@Composable
public fun KNetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Standard,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.colors(variant),
    content: @Composable () -> Unit
) {
    val buttonHeight = ButtonDefaults.height(size)
    val shapes = KNetTheme.shapes
    val typography = KNetTheme.typography

    val currentContainer = if (enabled) colors.containerColor else colors.disabledContainerColor
    val currentContent = if (enabled) colors.contentColor else colors.disabledContentColor
    val borderStroke = if (colors.borderColor != Color.Transparent) BorderStroke(1.dp, colors.borderColor) else null

    KNetSurface(
        modifier = modifier
            .height(buttonHeight)
            .clip(shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .handCursor(),
        color = currentContainer,
        contentColor = currentContent,
        border = borderStroke,
        shape = shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = KNetTheme.spacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProvideTextStyle(typography.labelMedium.copy(color = currentContent)) {
                content()
            }
        }
    }
}

/**
 * Text button primitive without surface container styling.
 */
@Composable
public fun KNetTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    KNetButton(
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Ghost,
        enabled = enabled,
        content = content
    )
}

/**
 * Toggle button primitive supporting boolean checked state.
 */
@Composable
public fun KNetToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val variant = if (checked) ButtonVariant.Primary else ButtonVariant.Secondary
    KNetButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        content = content
    )
}
