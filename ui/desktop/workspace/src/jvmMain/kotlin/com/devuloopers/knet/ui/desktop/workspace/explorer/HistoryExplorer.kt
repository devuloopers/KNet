package com.devuloopers.knet.ui.desktop.workspace.explorer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.placeholder.KNetEmptyStatePlaceholder

/**
 * History explorer view managing past HTTP request logs and session history.
 *
 * @param modifier Layout modifier.
 */
@Composable
fun HistoryExplorer(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(6.dp)) {
        KNetEmptyStatePlaceholder(
            title = "No History Logs",
            subtitle = "Captured proxy requests will appear here."
        )
    }
}

