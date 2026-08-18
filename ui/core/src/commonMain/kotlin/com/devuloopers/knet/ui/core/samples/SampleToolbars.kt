package com.devuloopers.knet.ui.core.samples

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.toolbar.KNetToolbar
import com.devuloopers.knet.ui.core.components.toolbar.ToolbarSpacer

@Composable
fun SampleToolbar(
    modifier: Modifier = Modifier
) {
    KNetToolbar(
        modifier = modifier,
        leading = { Text("Sample App") },
        trailing = { Text("v2.0.0") }
    ) {
        ToolbarSpacer()
    }
}
