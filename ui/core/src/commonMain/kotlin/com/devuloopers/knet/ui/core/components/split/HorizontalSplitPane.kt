package com.devuloopers.knet.ui.core.components.split

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import com.devuloopers.knet.ui.core.foundation.pointer.resizeHorizontalCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import kotlin.math.roundToInt

/** Shared visual boundary policy, independent of the accessible resize target. */
enum class SplitPaneDividerStyle {
    /** Existing wider gutter for layouts with independently framed panels. */
    GUTTER,
    /** One theme divider with an invisible overlaid drag target and interaction emphasis. */
    SINGLE_LINE,
}

/**
 * Controlled left/right split pane whose ratio is owned by the calling screen.
 *
 * The resize target overlays the pane edges rather than increasing the visible divider width. Hover,
 * focus and drag change only its color in [SplitPaneDividerStyle.SINGLE_LINE], preserving pane geometry.
 *
 * @param splitRatio Width fraction assigned to [firstPane].
 * @param onSplitRatioChange Receives pointer and keyboard ratio changes.
 * @param firstPane Left pane content; omit adjoining borders when using the single-line style.
 * @param secondPane Right pane content; omit adjoining borders when using the single-line style.
 * @param modifier Modifier applied to the complete split.
 * @param minSplitRatio Smallest allowed first-pane fraction.
 * @param maxSplitRatio Largest allowed first-pane fraction.
 * @param dividerStyle Opt-in thin boundary; existing callers retain the gutter appearance.
 */
@Composable
fun HorizontalSplitPane(
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    firstPane: @Composable (Modifier) -> Unit,
    secondPane: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    minSplitRatio: Float = 0.15f,
    maxSplitRatio: Float = 0.85f,
    dividerStyle: SplitPaneDividerStyle = SplitPaneDividerStyle.GUTTER,
) {
    val colors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions
    val density = LocalDensity.current
    val safeMin = minSplitRatio.coerceIn(0.05f, 0.95f)
    val safeMax = maxSplitRatio.coerceIn(safeMin, 0.95f)
    val clampedRatio = splitRatio.coerceIn(safeMin, safeMax)
    val currentRatio by rememberUpdatedState(clampedRatio)
    val currentRatioChange by rememberUpdatedState(onSplitRatioChange)
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    var dragging by remember { mutableStateOf(false) }
    val thin = dividerStyle == SplitPaneDividerStyle.SINGLE_LINE
    val slotWidth = if (thin) dimensions.dividerThickness else dimensions.splitterHitTargetSize
    val lineColor = if (thin && (hovered || focused || dragging)) colors.accent else colors.border

    fun updateRatio(candidate: Float) {
        currentRatioChange(candidate.coerceIn(safeMin, safeMax))
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val paneWidthPx = with(density) { (maxWidth - slotWidth).toPx().coerceAtLeast(0f) }
        val targetOffsetPx = with(density) {
            (paneWidthPx * clampedRatio + (slotWidth.toPx() - dimensions.splitterHitTargetSize.toPx()) / 2).roundToInt()
        }
        Row(modifier = Modifier.fillMaxSize()) {
            firstPane(Modifier.weight(clampedRatio))
            Box(Modifier.width(slotWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(if (thin) dimensions.dividerThickness else dimensions.splitterSize)
                    .fillMaxHeight().background(lineColor))
            }
            secondPane(Modifier.weight(1f - clampedRatio))
        }
        // Painting and input have separate widths. This topmost transparent target owns pointer
        // gestures at the seam while the underlying one-pixel slot remains the only visible line.
        Box(
            modifier = Modifier
                .offset { IntOffset(targetOffsetPx, 0) }
                .width(dimensions.splitterHitTargetSize)
                .fillMaxHeight()
                .resizeHorizontalCursor()
                .hoverable(interactions)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(clampedRatio, safeMin..safeMax)
                    stateDescription = "Left pane ${(clampedRatio * 100).toInt()} percent"
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { updateRatio(currentRatio - 0.02f); true }
                        Key.DirectionRight -> { updateRatio(currentRatio + 0.02f); true }
                        else -> false
                    }
                }
                .focusable(interactionSource = interactions)
                .pointerInput(paneWidthPx, safeMin, safeMax) {
                    var dragRatio = currentRatio
                    detectDragGestures(
                        onDragStart = { dragRatio = currentRatio; dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (paneWidthPx > 0f) {
                                dragRatio = (dragRatio + dragAmount.x / paneWidthPx).coerceIn(safeMin, safeMax)
                                currentRatioChange(dragRatio)
                            }
                        },
                    )
                },
        )
    }
}
