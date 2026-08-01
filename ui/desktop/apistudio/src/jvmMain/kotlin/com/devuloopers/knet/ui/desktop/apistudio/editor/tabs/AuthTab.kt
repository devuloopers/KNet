package com.devuloopers.knet.ui.desktop.apistudio.editor.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.input.KNetDropdown
import com.devuloopers.knet.ui.core.input.KNetInputField

/**
 * Authentication settings editor tab composable.
 */
@Composable
public fun AuthTab(
    authType: String,
    authToken: String,
    onAuthTypeChanged: (String) -> Unit,
    onAuthTokenChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        KNetDropdown(
            items = listOf("No Auth", "Bearer Token", "Basic Auth", "API Key"),
            selectedItem = authType,
            itemLabel = { it },
            onItemSelected = onAuthTypeChanged,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (authType != "No Auth") {
            KNetInputField(
                value = authToken,
                onValueChange = onAuthTokenChanged,
                placeholder = "Enter Token or Key..."
            )
        }
    }
}
