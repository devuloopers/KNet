package com.devuloopers.knet.ui.desktop.traffic.filter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.input.KNetDropdown

/**
 * Method filter dropdown (ALL, GET, POST, PUT, DELETE, PATCH).
 */
@Composable
public fun MethodFilter(
    selectedMethod: String,
    onMethodSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    KNetDropdown(
        items = listOf("ALL", "GET", "POST", "PUT", "DELETE", "PATCH"),
        selectedItem = selectedMethod,
        itemLabel = { it },
        onItemSelected = onMethodSelected,
        modifier = modifier
    )
}
