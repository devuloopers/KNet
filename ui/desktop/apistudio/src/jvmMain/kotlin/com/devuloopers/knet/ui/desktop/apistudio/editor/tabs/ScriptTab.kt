package com.devuloopers.knet.ui.desktop.apistudio.editor.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.input.KNetInputField

/**
 * Pre-request JavaScript script editor tab composable.
 */
@Composable
public fun ScriptTab(
    script: String,
    onScriptChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        KNetInputField(
            value = script,
            onValueChange = onScriptChanged,
            placeholder = "// Enter pre-request script...\ncontext.setHeader('X-Custom-Header', 'Value');",
            modifier = Modifier.height(180.dp)
        )
    }
}
