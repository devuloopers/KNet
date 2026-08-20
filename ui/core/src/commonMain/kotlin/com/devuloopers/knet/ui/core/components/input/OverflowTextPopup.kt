package com.devuloopers.knet.ui.core.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Positions a text-overflow preview adjacent to its anchor while keeping it inside the current window.
 *
 * @property offsetPx Gap between the anchor and preview in physical pixels.
 */
private class OverflowPopupPositionProvider(
    private val offsetPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = anchorBounds.left.coerceIn(0, maxX)
        val belowY = anchorBounds.bottom + offsetPx
        val aboveY = anchorBounds.top - popupContentSize.height - offsetPx
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val y = (if (belowY + popupContentSize.height <= windowSize.height) belowY else aboveY).coerceIn(0, maxY)
        return IntOffset(x, y)
    }
}

/** Owns the cancellable stationary-hover delay for one overflow anchor. */
private class OverflowHoverDelayController {
    var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }
}

/**
 * Hosts compact text content and displays its complete value in an anchored popup only when measured text exceeds
 * the usable anchor width. The popup is intentionally non-focusable and display-only.
 *
 * @param text Complete text used for measurement and preview presentation.
 * @param textStyle Exact inline style used to measure overflow.
 * @param enabled Whether overflow preview behavior is active.
 * @param modifier Layout and sizing applied to the anchor.
 * @param horizontalContentPadding Horizontal anchor space unavailable to text measurement.
 * @param hoverDebounceMs Stationary hover duration required before showing the preview.
 * @param contentAlignment Alignment for the caller-owned inline content.
 * @param content Inline content rendered inside the measured anchor.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun OverflowTextPopupHost(
    text: String,
    textStyle: TextStyle,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    horizontalContentPadding: Dp = 0.dp,
    hoverDebounceMs: Long = InputFieldConfig.Default.hoverDebounceMs,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val hoverDelayController = remember { OverflowHoverDelayController() }
    val textMeasurer = rememberTextMeasurer()

    var containerWidthPx by remember { mutableStateOf(0) }
    var isHovered by remember { mutableStateOf(false) }
    var isMouseStationary by remember { mutableStateOf(false) }

    val textWidthPx = remember(text, textStyle) {
        if (text.isEmpty()) 0 else textMeasurer.measure(text = text, style = textStyle).size.width
    }
    val horizontalContentPaddingPx = with(density) { horizontalContentPadding.roundToPx() }
    val isOverflowing = isOverflowTextPopupRequired(
        textWidthPx = textWidthPx,
        containerWidthPx = containerWidthPx,
        horizontalContentPaddingPx = horizontalContentPaddingPx
    )
    val shouldShowPopup = enabled && text.isNotEmpty() && isHovered && isMouseStationary && isOverflowing

    fun resetHoverTimer() {
        isMouseStationary = false
        hoverDelayController.cancel()
        if (enabled && isHovered) {
            hoverDelayController.job = coroutineScope.launch {
                delay(hoverDebounceMs)
                isMouseStationary = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose(hoverDelayController::cancel)
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerWidthPx = it.width }
            .onPointerEvent(PointerEventType.Enter) {
                isHovered = true
                resetHoverTimer()
            }
            .onPointerEvent(PointerEventType.Move) {
                if (isHovered) resetHoverTimer()
            }
            .onPointerEvent(PointerEventType.Exit) {
                isHovered = false
                isMouseStationary = false
                hoverDelayController.cancel()
            },
        contentAlignment = contentAlignment
    ) {
        content()

        if (shouldShowPopup) {
            Popup(
                popupPositionProvider = remember(density) {
                    OverflowPopupPositionProvider(with(density) { 4.dp.roundToPx() })
                },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {}
                        .widthIn(max = 600.dp)
                        .clip(shapes.small)
                        .background(themeColors.surface)
                        .border(1.dp, themeColors.border, shapes.small)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = text,
                        style = typography.bodySmall.copy(color = themeColors.textPrimary)
                    )
                }
            }
        }
    }
}

/**
 * Resolves whether measured text exceeds the usable width of its anchor.
 *
 * @param textWidthPx Measured text width in physical pixels.
 * @param containerWidthPx Measured anchor width in physical pixels.
 * @param horizontalContentPaddingPx Combined horizontal space unavailable to text.
 * @return `true` only when a measured anchor exists and text exceeds its usable width.
 */
internal fun isOverflowTextPopupRequired(
    textWidthPx: Int,
    containerWidthPx: Int,
    horizontalContentPaddingPx: Int
): Boolean {
    if (containerWidthPx <= 0) return false
    val usableWidthPx = (containerWidthPx - horizontalContentPaddingPx).coerceAtLeast(0)
    return textWidthPx > usableWidthPx
}
