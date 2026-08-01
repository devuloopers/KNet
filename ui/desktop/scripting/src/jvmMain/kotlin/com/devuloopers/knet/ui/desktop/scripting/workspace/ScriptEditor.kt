package com.devuloopers.knet.ui.desktop.scripting.workspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode

/**
 * Script Editor wrapping KNetCodeEditor.
 */
@Composable
public fun ScriptEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    KNetCodeEditor(
        code = code,
        mode = EditorMode.Editable(onCodeChange = onCodeChange),
        modifier = modifier.fillMaxSize()
    )
}
