package com.devuloopers.knet.ui.desktop.apistudio.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.input.KNetDropdown

/**
 * Environment selector dropdown composable.
 *
 * @param selectedEnvironment Currently selected environment name.
 * @param onEnvironmentSelected Callback when an environment is picked.
 * @param modifier Layout modifier.
 */
@Composable
public fun EnvironmentSelector(
    selectedEnvironment: String,
    onEnvironmentSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    KNetDropdown(
        items = listOf("No Environment", "Development", "QA", "Staging", "Production"),
        selectedItem = selectedEnvironment,
        itemLabel = { it },
        onItemSelected = onEnvironmentSelected,
        modifier = modifier
    )
}
