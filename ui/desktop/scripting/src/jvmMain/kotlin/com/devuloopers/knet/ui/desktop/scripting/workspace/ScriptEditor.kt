package com.devuloopers.knet.ui.desktop.scripting.workspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.desktop.httppanel.editor.ScriptEditor as HttpScriptEditor
import com.devuloopers.knet.ui.desktop.httppanel.model.ScriptState

/**
 * Script Editor delegating to the central HTTP panel ScriptEditor facade.
 */
@Composable
fun ScriptEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    HttpScriptEditor(
        state = ScriptState(preRequestScript = code),
        onStateChange = { onCodeChange(it.preRequestScript) },
        modifier = modifier.fillMaxSize()
    )
}
