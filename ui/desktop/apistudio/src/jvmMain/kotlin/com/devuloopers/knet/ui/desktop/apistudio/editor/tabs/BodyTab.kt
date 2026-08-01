package com.devuloopers.knet.ui.desktop.apistudio.editor.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.input.KNetDropdown
import com.devuloopers.knet.ui.core.input.KNetInputField

/**
 * Request body payload editor tab composable.
 */
@Composable
public fun BodyTab(
    bodyType: String,
    bodyPayload: String,
    onBodyTypeChanged: (String) -> Unit,
    onBodyPayloadChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        KNetDropdown(
            items = listOf("None", "JSON", "Form Data", "Raw Text", "Binary"),
            selectedItem = bodyType,
            itemLabel = { it },
            onItemSelected = onBodyTypeChanged,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (bodyType != "None") {
            KNetInputField(
                value = bodyPayload,
                onValueChange = onBodyPayloadChanged,
                placeholder = "Enter request body payload...",
                modifier = Modifier.height(180.dp)
            )
        }
    }
}
