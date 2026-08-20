package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Invisible Compose text-input connection used while the editor owns a document-level selection.
 *
 * Virtualized line inputs cannot represent a range spanning multiple logical lines, and the selected active
 * endpoint may not be composed. This bridge retains keyboard, dead-key, and IME input without storing document
 * content. Only committed text leaves this Compose adapter; the editor session remains the mutation owner.
 */
@Composable
internal fun SelectionTextInputBridge(
    active: Boolean,
    onCommittedText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val currentOnCommittedText by rememberUpdatedState(onCommittedText)
    var inputValue by remember { mutableStateOf(TextFieldValue()) }

    LaunchedEffect(active) {
        inputValue = TextFieldValue()
        if (active) runCatching { focusRequester.requestFocus() }
    }

    if (!active) return

    BasicTextField(
        value = inputValue,
        onValueChange = { updated ->
            inputValue = updated
            committedSelectionInput(updated)?.let { committedText ->
                inputValue = TextFieldValue()
                currentOnCommittedText(committedText)
            }
        },
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        modifier = modifier
            .size(1.dp)
            .alpha(0f)
            .focusRequester(focusRequester)
            .clearAndSetSemantics { }
    )
}

/**
 * Returns complete committed replacement text while retaining an active IME composition in the bridge.
 */
internal fun committedSelectionInput(value: TextFieldValue): String? {
    if (value.composition != null || value.text.isEmpty()) return null
    return value.text
}
