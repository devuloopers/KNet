package com.devuloopers.knet.ui.core.components.button

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * High-density IDE button primitive with native loading indicator support.
 * Features non-blocking smooth state-switch animations for colors, container size, and loading content crossfade.
 *
 * @param onClick Execution callback when clicked.
 * @param modifier Layout modifier.
 * @param variant Visual button variant (Primary, Secondary, Tertiary, Ghost, Danger).
 * @param size Button height and density size.
 * @param enabled Interactivity toggle.
 * @param loading Asynchronous loading toggle (disables click interaction and shows spinner).
 * @param colors Custom button colors override.
 * @param content Slot layout for button text and icons.
 */
@Composable
public fun KNetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Standard,
    enabled: Boolean = true,
    loading: Boolean = false,
    colors: ButtonColors = ButtonDefaults.colors(variant),
    content: @Composable () -> Unit
) {
    val buttonHeight = ButtonDefaults.height(size)
    val shapes = KNetTheme.shapes
    val typography = KNetTheme.typography

    val isClickable = enabled && !loading
    val animatedContainer by animateColorAsState(
        targetValue = if (enabled) colors.containerColor else colors.disabledContainerColor,
        animationSpec = tween(durationMillis = 150),
        label = "KNetButtonContainerColor"
    )
    val animatedContent by animateColorAsState(
        targetValue = if (enabled) colors.contentColor else colors.disabledContentColor,
        animationSpec = tween(durationMillis = 150),
        label = "KNetButtonContentColor"
    )
    val borderStroke = if (colors.borderColor != Color.Transparent) BorderStroke(1.dp, colors.borderColor) else null

    KNetSurface(
        modifier = modifier
            .height(buttonHeight)
            .wrapContentWidth()
            .clip(shapes.small)
            .animateContentSize(animationSpec = tween(durationMillis = 150))
            .clickable(enabled = isClickable, onClick = onClick)
            .handCursor(),
        color = animatedContainer,
        contentColor = animatedContent,
        border = borderStroke,
        shape = shapes.small
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "KNetButtonLoadingContent"
        ) { isLoading ->
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentWidth()
                    .padding(horizontal = KNetTheme.spacing.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = animatedContent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                ProvideTextStyle(typography.labelMedium.copy(color = animatedContent)) {
                    content()
                }
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
