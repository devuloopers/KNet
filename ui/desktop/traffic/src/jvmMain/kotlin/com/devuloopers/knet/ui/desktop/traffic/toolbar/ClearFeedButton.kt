package com.devuloopers.knet.ui.desktop.traffic.toolbar

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/**
 * Traffic feed clear button.
 */
@Composable
public fun ClearFeedButton(
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(onClick = onClear, modifier = modifier) {
        Text("Clear Feed", fontSize = 11.sp)
    }
}
