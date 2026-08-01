package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.desktop.apistudio.component.EnvironmentSelector
import com.devuloopers.knet.ui.desktop.apistudio.component.ExecutionToolbar
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState

/**
 * Top HTTP request action toolbar hosting URL input, Method dropdown, Send button, and Environment selector.
 *
 * @param url Current URL.
 * @param method Current HTTP method.
 * @param selectedEnvironment Active environment name.
 * @param executionState Current execution status.
 * @param onUrlChanged Callback when URL changes.
 * @param onMethodChanged Callback when method changes.
 * @param onEnvironmentSelected Callback when environment changes.
 * @param onSend Callback when Send is clicked.
 * @param onCancel Callback when Cancel is clicked.
 * @param modifier Layout modifier.
 */
@Composable
public fun RequestToolbar(
    url: String,
    method: String,
    selectedEnvironment: String,
    executionState: ExecutionState,
    onUrlChanged: (String) -> Unit,
    onMethodChanged: (String) -> Unit,
    onEnvironmentSelected: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MethodSelector(selectedMethod = method, onMethodSelected = onMethodChanged)
        UrlBar(url = url, onUrlChanged = onUrlChanged, modifier = Modifier.weight(1f))
        ExecutionToolbar(executionState = executionState, onSend = onSend, onCancel = onCancel)
        EnvironmentSelector(selectedEnvironment = selectedEnvironment, onEnvironmentSelected = onEnvironmentSelected)
    }
}
