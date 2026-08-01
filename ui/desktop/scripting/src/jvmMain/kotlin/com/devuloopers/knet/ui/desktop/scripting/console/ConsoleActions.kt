package com.devuloopers.knet.ui.desktop.scripting.console

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.input.CopyActionButton
import com.devuloopers.knet.ui.desktop.scripting.model.ConsoleLogEntry

/**
 * Action button container to copy console log lines.
 */
@Composable
public fun ConsoleActions(
    logs: List<ConsoleLogEntry>,
    modifier: Modifier = Modifier
) {
    val fullLogText = logs.joinToString("\n") { "[${it.level.name}] ${it.message}" }
    Row(modifier = modifier.padding(vertical = 4.dp)) {
        CopyActionButton(textToCopy = fullLogText)
    }
}
