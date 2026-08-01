package com.devuloopers.knet.ui.desktop.workspace.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Resizable vertical/horizontal splitter divider handle for workspace panels.
 *
 * @param modifier Layout modifier.
 * @param thickness Splitter bar thickness.
 */
@Composable
fun WorkspaceSplitter(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp
) {
    Box(
        modifier = modifier
            .width(thickness)
            .fillMaxHeight()
            .background(KNetColors.BorderDark)
    )
}
