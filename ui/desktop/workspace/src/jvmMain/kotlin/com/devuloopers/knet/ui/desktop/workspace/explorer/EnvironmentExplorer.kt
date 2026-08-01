package com.devuloopers.knet.ui.desktop.workspace.explorer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.feedback.EmptyState

/**
 * Environment explorer view managing active environment variables and key-value presets.
 *
 * @param modifier Layout modifier.
 */
@Composable
fun EnvironmentExplorer(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(6.dp)) {
        EmptyState(
            title = "No Environments",
            description = "Add an environment to manage variables."
        )
    }
}
