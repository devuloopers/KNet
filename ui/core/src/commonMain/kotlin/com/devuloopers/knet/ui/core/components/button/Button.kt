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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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
 * @param loading Asynchronous loading toggle that shows a spinner and disables interaction by default.
 * @param clickableWhileLoading Explicit opt-in for loading controls that expose a valid cancellation action.
 * @param colors Custom button colors override.
 * @param role Accessibility role exposed by the click target.
 * @param content Slot layout for button text and icons.
 */
@Composable
fun KNetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Standard,
    enabled: Boolean = true,
    loading: Boolean = false,
    clickableWhileLoading: Boolean = false,
    colors: ButtonColors = ButtonDefaults.colors(variant),
    role: Role = Role.Button,
    content: @Composable () -> Unit
) {
    val buttonHeight = ButtonDefaults.height(size)
    val shapes = KNetTheme.shapes
    val typography = KNetTheme.typography
    val motion = KNetTheme.motion
    val duration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()

    val isClickable = isKNetButtonClickable(
        enabled = enabled,
        loading = loading,
        clickableWhileLoading = clickableWhileLoading
    )
    val animatedContainer by animateColorAsState(
        targetValue = when {
            !enabled -> colors.disabledContainerColor
            hovered -> KNetTheme.colors.interaction.hoverOverlay.compositeOver(colors.containerColor)
            else -> colors.containerColor
        },
        animationSpec = tween(durationMillis = duration),
        label = "KNetButtonContainerColor"
    )
    val animatedContent by animateColorAsState(
        targetValue = if (enabled) colors.contentColor else colors.disabledContentColor,
        animationSpec = tween(durationMillis = duration),
        label = "KNetButtonContentColor"
    )
    val borderStroke = when {
        focused -> BorderStroke(1.dp, KNetTheme.colors.interaction.focusRing)
        colors.borderColor != Color.Transparent -> BorderStroke(1.dp, colors.borderColor)
        else -> null
    }

    KNetSurface(
        modifier = modifier
            .height(buttonHeight)
            .clip(shapes.small)
            .animateContentSize(animationSpec = tween(durationMillis = duration))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isClickable,
                role = role,
                onClick = onClick
            )
            .handCursor(isClickable),
        color = animatedContainer,
        contentColor = animatedContent,
        border = borderStroke,
        shape = shapes.small
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                fadeIn(animationSpec = tween(duration)) togetherWith fadeOut(animationSpec = tween(duration))
            },
            label = "KNetButtonLoadingContent",
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center
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

/** Resolves pointer and keyboard interaction without changing the button's visual loading state. */
internal fun isKNetButtonClickable(
    enabled: Boolean,
    loading: Boolean,
    clickableWhileLoading: Boolean
): Boolean = enabled && (!loading || clickableWhileLoading)

/**
 * Text button primitive without surface container styling.
 */
@Composable
fun KNetTextButton(
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
fun KNetToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val variant = if (checked) ButtonVariant.Primary else ButtonVariant.Secondary
    KNetButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.semantics {
            toggleableState = ToggleableState(checked)
        },
        variant = variant,
        enabled = enabled,
        role = Role.Switch,
        content = content
    )
}
