package com.devuloopers.knet.ui.core.components.split

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.resizeHorizontalCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Resizable horizontal split pane composable.
 *
 * @param firstPane Content composable for the left pane receiving Modifier with proportional weight.
 * @param secondPane Content composable for the right pane receiving Modifier with proportional weight.
 * @param modifier Compose modifier applied to the split container.
 * @param initialSplitRatio Initial width ratio between left and right panes (default: 0.5f).
 * @param minSplitRatio Minimum allowable split ratio for the first pane (default: 0.15f).
 * @param maxSplitRatio Maximum allowable split ratio for the first pane (default: 0.85f).
 */
@Composable
public fun HorizontalSplitPane(
    firstPane: @Composable (Modifier) -> Unit,
    secondPane: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    initialSplitRatio: Float = 0.5f,
    minSplitRatio: Float = 0.15f,
    maxSplitRatio: Float = 0.85f
) {
    var splitRatio by remember { mutableStateOf(initialSplitRatio) }
    val themeColors = KNetTheme.colors
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val safeMin = minSplitRatio.coerceIn(0.05f, 0.95f)
        val safeMax = maxSplitRatio.coerceIn(safeMin, 0.95f)
        val clampedRatio = splitRatio.coerceIn(safeMin, safeMax)

        Row(modifier = Modifier.fillMaxSize()) {
            firstPane(Modifier.weight(clampedRatio))

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(themeColors.border)
                    .resizeHorizontalCursor()
                    .pointerInput(totalWidthPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (totalWidthPx > 0f) {
                                val delta = dragAmount.x / totalWidthPx
                                splitRatio = (splitRatio + delta).coerceIn(safeMin, safeMax)
                            }
                        }
                    }
            )

            secondPane(Modifier.weight(1f - clampedRatio))
        }
    }
}

