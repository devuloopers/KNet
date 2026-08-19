package com.devuloopers.knet.ui.core.components.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Reusable multiline text area delegating to the selection-preserving KNet input core.
 *
 * @param value Current text.
 * @param onValueChange Receives text edits.
 * @param modifier Modifier applied to the field.
 * @param placeholder Hint shown for an empty value.
 * @param enabled Whether editing is enabled.
 * @param readOnly Whether selection is allowed without editing.
 * @param isError Whether error styling is shown.
 */
@Composable
fun KNetTextArea(
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
