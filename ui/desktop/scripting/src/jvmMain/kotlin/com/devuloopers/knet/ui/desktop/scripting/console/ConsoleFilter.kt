package com.devuloopers.knet.ui.desktop.scripting.console

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown

/**
 * Dropdown filter selector for ConsoleView levels.
 */
@Composable
fun ConsoleFilter(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf("ALL", "INFO", "WARN", "ERROR", "DEBUG")
    Row(modifier = modifier.padding(vertical = 4.dp)) {
        KNetDropdown(
            selectedItem = selectedFilter,
            items = options,
            onItemSelected = onFilterSelected,
            itemText = { it },
            modifier = Modifier.width(120.dp)
        )
    }
}
