package com.devuloopers.knet.ui.desktop.traffic.toolbar

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/**
 * Traffic feed auto-scroll toggle button.
 */
@Composable
public fun AutoScrollButton(
    autoScroll: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(onClick = onToggle, modifier = modifier) {
        Text(if (autoScroll) "AutoScroll: ON" else "AutoScroll: OFF", fontSize = 11.sp)
    }
}
