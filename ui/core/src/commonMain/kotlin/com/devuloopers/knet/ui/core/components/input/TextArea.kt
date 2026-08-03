package com.devuloopers.knet.ui.core.components.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
public fun KNetTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false
) {
    KNetMultilineField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError
    )
}
