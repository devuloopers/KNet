package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.desktop.codeeditor.model.LineSelectionBounds
import com.devuloopers.knet.ui.desktop.codeeditor.modifier.selectionHighlight
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens

/**
 * Read-only line content composable for [com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody].
 */
@Composable
fun ReadOnlyLineContent(
    highlightedText: AnnotatedString,
    isWordWrapEnabled: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    lineSelectionBounds: LineSelectionBounds? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()
    val textLayoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!isWordWrapEnabled) Modifier.horizontalScroll(horizontalScrollState)
                else Modifier
            )
            .selectionHighlight(lineSelectionBounds, textLayoutResult.value, fontSize)
    ) {
        Text(
            text = highlightedText,
            onTextLayout = {
                textLayoutResult.value = it
                onTextLayout?.invoke(it)
            },
            style = CodeEditorTokens.editorTextStyle(
                fontSize = fontSize,
                lineHeight = lineHeight
            ).copy(
                color = Color.White,
                fontFamily = FontFamily.Monospace
            ),
            softWrap = isWordWrapEnabled,
            maxLines = if (isWordWrapEnabled) Int.MAX_VALUE else 1
        )
    }
}
