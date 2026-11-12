package com.devuloopers.knet.ui.core.components.tooltip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import kotlinx.coroutines.delay

/** Shared timing and wrapping limits for contextual descriptions, independent of inline control sizing. */
object KNetTooltipDefaults {
    /** Delay before pointer hover reveals contextual help. */
    const val ShowDelayMillis: Long = 500L
    /** Maximum paragraph width; popup constraints further bound help to the current window. */
    val MaximumWidth = 360.dp
}

/** Preferred placement for contextual help, with window-bounded fallback when space is limited. */
enum class KNetTooltipPlacement {
    /** Prefer below an ordinary control, moving above it near the bottom of the window. */
    Below,
    /** Prefer either side of a menu row so help does not cover adjacent options. */
    Beside,
}

/**
 * Hosts optional descriptive help without reserving layout space or taking keyboard focus.
 *
 * Blank descriptions retain the original content. Leaving hover, changing the description, disabling help
 * or disposing the anchor cancels pending presentation. Focus never opens or keeps this hover-only popup alive.
 * Escape dismisses visible help until interaction changes.
 * Controls must use the supplied interaction source for their focusable/clickable element and expose the same
 * description in its accessibility semantics.
 *
 * @param description Optional explanatory text, allowed to wrap inside the shared tooltip.
 * @param enabled Whether contextual help may appear, independently of the control's enabled state.
 * @param modifier Layout applied to the help anchor.
 * @param placement Preferred side of the anchor; the popup always remains window-bounded.
 * @param avoidBounds Optional surrounding menu bounds in window pixels, kept clear when side placement cannot fit.
 * @param content Inline control receiving the shared hover/focus interaction source.
 */
@Composable
fun KNetTooltipHost(
    description: String?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    placement: KNetTooltipPlacement = KNetTooltipPlacement.Below,
    avoidBounds: IntRect? = null,
    content: @Composable (MutableInteractionSource) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var ready by remember(description, enabled, hovered) { mutableStateOf(false) }
    var dismissed by remember(description, enabled, hovered) { mutableStateOf(false) }
    val eligible = enabled && !description.isNullOrBlank() && hovered
    LaunchedEffect(enabled, description, hovered) {
        if (eligible) {
            delay(KNetTooltipDefaults.ShowDelayMillis)
            ready = true
        }
    }
    val visible = eligible && ready && !dismissed
    val density = LocalDensity.current
    val gap = with(density) { KNetTheme.spacing.xs.roundToPx() }
    Box(modifier.hoverable(interactionSource, enabled && !description.isNullOrBlank())
        .onPreviewKeyEvent { event ->
            if (visible && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                dismissed = true
                true
            } else false
        }) {
        content(interactionSource)
        if (visible) {
            Popup(
                popupPositionProvider = remember(gap, placement, avoidBounds) {
                    DescriptionPopupPositionProvider(gap, placement, avoidBounds)
                },
                properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
            ) {
                val colors = KNetTheme.colors
                val shape = KNetTheme.shapes.small
                Text(
                    text = description.orEmpty(),
                    style = KNetTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    modifier = Modifier.widthIn(max = KNetTooltipDefaults.MaximumWidth)
                        .shadow(KNetTheme.elevation.level3, shape)
                        .background(colors.surfaceVariant, shape)
                        .border(1.dp, colors.border, shape)
                        .padding(KNetTheme.spacing.sm),
                )
            }
        }
    }
}

/** Keeps descriptive help beside its anchor and inside the window in both layout directions. */
internal class DescriptionPopupPositionProvider(
    private val gap: Int,
    private val placement: KNetTooltipPlacement = KNetTooltipPlacement.Below,
    private val avoidBounds: IntRect? = null,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maximumX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val preferredX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
            else anchorBounds.right - popupContentSize.width
        val exclusion = avoidBounds ?: anchorBounds
        if (placement == KNetTooltipPlacement.Beside) {
            val right = exclusion.right + gap
            val left = exclusion.left - popupContentSize.width - gap
            val sideX = if (layoutDirection == LayoutDirection.Ltr) {
                if (right <= maximumX) right else left.takeIf { it >= 0 }
            } else {
                if (left >= 0) left else right.takeIf { it <= maximumX }
            }
            if (sideX != null) return IntOffset(sideX,
                anchorBounds.top.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
        }
        val below = exclusion.bottom + gap
        val above = exclusion.top - popupContentSize.height - gap
        val preferredY = if (below + popupContentSize.height <= windowSize.height) below else above
        return IntOffset(preferredX.coerceIn(0, maximumX),
            preferredY.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}
