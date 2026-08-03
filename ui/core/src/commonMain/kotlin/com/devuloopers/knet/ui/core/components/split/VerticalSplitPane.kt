package com.devuloopers.knet.ui.core.components.split

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.resizeVerticalCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun VerticalSplitPane(
    topPane: @Composable (Modifier) -> Unit,
    bottomPane: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    initialSplitRatio: Float = 0.5f
) {
    var splitRatio by remember { mutableStateOf(initialSplitRatio) }
    val themeColors = KNetTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        topPane(Modifier.weight(splitRatio.coerceIn(0.1f, 0.9f)))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(themeColors.border)
                .resizeVerticalCursor()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount.y / size.height
                        splitRatio = (splitRatio + delta).coerceIn(0.1f, 0.9f)
                    }
                }
        )

        bottomPane(Modifier.weight(1f - splitRatio.coerceIn(0.1f, 0.9f)))
    }
}
