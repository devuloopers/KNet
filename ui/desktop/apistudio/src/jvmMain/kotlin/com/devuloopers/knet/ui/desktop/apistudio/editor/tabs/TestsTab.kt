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
 * Post-execution test assertions script editor tab composable.
 */
@Composable
public fun TestsTab(
    testScript: String,
    onTestScriptChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        KNetInputField(
            value = testScript,
            onValueChange = onTestScriptChanged,
            placeholder = "// Enter test script...\nassert(response.statusCode == 200);",
            modifier = Modifier.height(180.dp)
        )
    }
}
