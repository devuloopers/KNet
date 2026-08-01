package com.devuloopers.knet.ui.core.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Desktop panel docking manager supporting split arrangements:
 * - Left panel slot (e.g. Workspace tree, collections)
 * - Right panel slot (e.g. Inspector panel)
 * - Bottom panel slot (e.g. Console, traffic details)
 * - Center main content slot
 *
 * @param centerContent Main center feature content slot.
 * @param leftDock Optional left dock panel slot.
 * @param rightDock Optional right dock panel slot.
 * @param bottomDock Optional bottom dock panel slot.
 * @param leftDockWidth Width of left panel in Dp.
 * @param rightDockWidth Width of right panel in Dp.
 * @param bottomDockHeight Height of bottom panel in Dp.
 * @param modifier Layout modifier.
 */
@Composable
public fun DockLayout(
    centerContent: @Composable () -> Unit,
    leftDock: (@Composable () -> Unit)? = null,
    rightDock: (@Composable () -> Unit)? = null,
    bottomDock: (@Composable () -> Unit)? = null,
    leftDockWidth: Dp = 260.dp,
    rightDockWidth: Dp = 340.dp,
    bottomDockHeight: Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (leftDock != null) {
                Box(modifier = Modifier.width(leftDockWidth).fillMaxHeight()) {
                    leftDock()
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                centerContent()
            }
            if (rightDock != null) {
                Box(modifier = Modifier.width(rightDockWidth).fillMaxHeight()) {
                    rightDock()
                }
            }
        }
        if (bottomDock != null) {
            Box(modifier = Modifier.fillMaxWidth().height(bottomDockHeight)) {
                bottomDock()
            }
        }
    }
}
