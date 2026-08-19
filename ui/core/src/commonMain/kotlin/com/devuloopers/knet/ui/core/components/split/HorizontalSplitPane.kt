package com.devuloopers.knet.ui.core.components.split

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.resizeHorizontalCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Controlled left/right split pane whose ratio is owned by the calling screen.
 *
 * @param splitRatio Width fraction assigned to [firstPane].
 * @param onSplitRatioChange Receives pointer and keyboard ratio changes.
 * @param firstPane Left pane content.
 * @param secondPane Right pane content.
 * @param modifier Modifier applied to the complete split.
 * @param minSplitRatio Smallest allowed first-pane fraction.
 * @param maxSplitRatio Largest allowed first-pane fraction.
 */
@Composable
fun HorizontalSplitPane(
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    firstPane: @Composable (Modifier) -> Unit,
    secondPane: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    minSplitRatio: Float = 0.15f,
    maxSplitRatio: Float = 0.85f
) {
    val colors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions
    val density = LocalDensity.current
    val safeMin = minSplitRatio.coerceIn(0.05f, 0.95f)
    val safeMax = maxSplitRatio.coerceIn(safeMin, 0.95f)
    val clampedRatio = splitRatio.coerceIn(safeMin, safeMax)
    val currentRatio by rememberUpdatedState(clampedRatio)
    val currentRatioChange by rememberUpdatedState(onSplitRatioChange)

    fun updateRatio(candidate: Float) {
        onSplitRatioChange(candidate.coerceIn(safeMin, safeMax))
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        Row(modifier = Modifier.fillMaxSize()) {
            firstPane(Modifier.weight(clampedRatio))
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .resizeHorizontalCursor()
                    .focusable()
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(clampedRatio, safeMin..safeMax)
                        stateDescription = "Left pane ${(clampedRatio * 100).toInt()} percent"
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                updateRatio(clampedRatio - 0.02f)
                                true
                            }
                            Key.DirectionRight -> {
                                updateRatio(clampedRatio + 0.02f)
                                true
                            }
                            else -> false
                        }
                    }
                    .pointerInput(totalWidthPx, safeMin, safeMax) {
                        var dragRatio = currentRatio
                        detectDragGestures(
                            onDragStart = { dragRatio = currentRatio },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (totalWidthPx > 0f) {
                                    dragRatio = (dragRatio + dragAmount.x / totalWidthPx).coerceIn(safeMin, safeMax)
                                    currentRatioChange(dragRatio)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(dimensions.splitterSize)
                        .fillMaxHeight()
                        .background(colors.border)
                )
            }
            secondPane(Modifier.weight(1f - clampedRatio))
        }
    }
}
