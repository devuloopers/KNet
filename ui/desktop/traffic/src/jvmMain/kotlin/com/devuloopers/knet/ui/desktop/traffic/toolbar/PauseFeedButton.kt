package com.devuloopers.knet.ui.desktop.traffic.toolbar

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/**
 * Traffic feed pause/resume button.
 */
@Composable
public fun PauseFeedButton(
    isPaused: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(onClick = onToggle, modifier = modifier) {
        Text(if (isPaused) "Resume Feed" else "Pause Feed", fontSize = 11.sp)
    }
}
