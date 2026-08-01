package com.devuloopers.knet.ui.core.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Two-pane resizable split layout view container for desktop UI panels.
 *
 * @param leftContent Left panel composable slot.
 * @param rightContent Right panel composable slot.
 * @param modifier Layout modifier.
 * @param leftPanelWidth Initial width of the left panel in Dp.
 */
@Composable
public fun SplitLayout(
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leftPanelWidth: Dp = 320.dp
) {
    Row(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(leftPanelWidth).fillMaxHeight()) {
            leftContent()
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(KNetColors.BorderDark)
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            rightContent()
        }
    }
}
