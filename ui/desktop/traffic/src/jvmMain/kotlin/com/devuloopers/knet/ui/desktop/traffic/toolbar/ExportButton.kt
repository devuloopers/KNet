package com.devuloopers.knet.ui.desktop.traffic.toolbar

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/**
 * Traffic feed export button.
 */
@Composable
public fun ExportButton(
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(onClick = onExport, modifier = modifier) {
        Text("Export HAR", fontSize = 11.sp)
    }
}
