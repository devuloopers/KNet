package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.input.KNetDropdown

/**
 * HTTP Method picker dropdown composable (GET, POST, PUT, DELETE, PATCH).
 *
 * @param selectedMethod Currently selected HTTP method.
 * @param onMethodSelected Callback when a method is selected.
 * @param modifier Layout modifier.
 */
@Composable
public fun MethodSelector(
    selectedMethod: String,
    onMethodSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    KNetDropdown(
        items = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"),
        selectedItem = selectedMethod,
        itemLabel = { it },
        onItemSelected = onMethodSelected,
        modifier = modifier.width(110.dp)
    )
}
