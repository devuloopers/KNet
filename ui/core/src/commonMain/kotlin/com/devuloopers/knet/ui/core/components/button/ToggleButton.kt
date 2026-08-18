package com.devuloopers.knet.ui.core.components.button

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    KNetToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        content = content
    )
}
