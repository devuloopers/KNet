package com.devuloopers.knet.ui.core.components.split

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.devuloopers.knet.ui.core.foundation.pointer.resizeVerticalCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Controlled top/bottom split pane whose pointer delta is measured against the full container height.
 *
 * @param splitRatio Height fraction assigned to [topPane].
 * @param onSplitRatioChange Receives pointer and keyboard ratio changes.
 * @param topPane Upper pane content.
 * @param bottomPane Lower pane content.
 * @param modifier Modifier applied to the complete split.
 * @param minSplitRatio Smallest allowed top-pane fraction.
 * @param maxSplitRatio Largest allowed top-pane fraction.
 */
@Composable
fun VerticalSplitPane(
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    topPane: @Composable (Modifier) -> Unit,
    bottomPane: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    minSplitRatio: Float = 0.1f,
    maxSplitRatio: Float = 0.9f
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
        val totalHeightPx = with(density) { maxHeight.toPx() }
        Column(modifier = Modifier.fillMaxSize()) {
            topPane(Modifier.weight(clampedRatio))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .resizeVerticalCursor()
                    .focusable()
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(clampedRatio, safeMin..safeMax)
                        stateDescription = "Top pane ${(clampedRatio * 100).toInt()} percent"
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> {
                                updateRatio(clampedRatio - 0.02f)
                                true
                            }
                            Key.DirectionDown -> {
                                updateRatio(clampedRatio + 0.02f)
                                true
                            }
                            else -> false
                        }
                    }
                    .pointerInput(totalHeightPx, safeMin, safeMax) {
                        var dragRatio = currentRatio
                        detectDragGestures(
                            onDragStart = { dragRatio = currentRatio },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (totalHeightPx > 0f) {
                                    dragRatio = (dragRatio + dragAmount.y / totalHeightPx).coerceIn(safeMin, safeMax)
                                    currentRatioChange(dragRatio)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(dimensions.splitterSize)
                        .background(colors.border)
                )
            }
            bottomPane(Modifier.weight(1f - clampedRatio))
        }
    }
}
