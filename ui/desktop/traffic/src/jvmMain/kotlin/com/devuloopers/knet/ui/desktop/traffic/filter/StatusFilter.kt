package com.devuloopers.knet.ui.desktop.traffic.filter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.input.KNetDropdown

/**
 * Status group filter dropdown (ALL, 2xx, 3xx, 4xx, 5xx, ERR).
 */
@Composable
public fun StatusFilter(
    selectedStatus: String,
    onStatusSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    KNetDropdown(
        items = listOf("ALL", "2xx Success", "3xx Redirect", "4xx Client Err", "5xx Server Err"),
        selectedItem = selectedStatus,
        itemLabel = { it },
        onItemSelected = onStatusSelected,
        modifier = modifier
    )
}
