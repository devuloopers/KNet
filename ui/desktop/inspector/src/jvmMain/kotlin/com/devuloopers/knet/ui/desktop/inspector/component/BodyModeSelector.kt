package com.devuloopers.knet.ui.desktop.inspector.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.input.KNetDropdown

/**
 * Body viewer mode picker dropdown (Pretty, Raw, Hex, Image).
 */
@Composable
public fun BodyModeSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    KNetDropdown(
        items = listOf("Pretty", "Raw", "Hex", "Image"),
        selectedItem = selectedMode,
        itemLabel = { it },
        onItemSelected = onModeSelected,
        modifier = modifier
    )
}
