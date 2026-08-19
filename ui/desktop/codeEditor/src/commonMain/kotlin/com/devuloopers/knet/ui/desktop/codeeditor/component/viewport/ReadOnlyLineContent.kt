package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.desktop.codeeditor.modifier.selectionHighlight
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens

/** Renders one non-editing line using the viewport's shared horizontal scroll state. */
@Composable
internal fun ReadOnlyLineContent(
    highlightedText: AnnotatedString,
    isWordWrapEnabled: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    horizontalScrollState: ScrollState,
    lineSelectionBounds: LineSelectionBounds? = null,
    textLayoutResult: TextLayoutResult? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .editorLineContentHeight(isWordWrapEnabled)
            .then(if (!isWordWrapEnabled) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
            .selectionHighlight(lineSelectionBounds, textLayoutResult, fontSize),
        contentAlignment = if (isWordWrapEnabled) Alignment.TopStart else Alignment.CenterStart
    ) {
        Text(
            text = highlightedText,
            onTextLayout = { onTextLayout?.invoke(it) },
            style = CodeEditorTokens.editorTextStyle(fontSize = fontSize, lineHeight = lineHeight).copy(
                color = Color.White,
                fontFamily = FontFamily.Monospace
            ),
            softWrap = isWordWrapEnabled,
            maxLines = if (isWordWrapEnabled) Int.MAX_VALUE else 1
        )
    }
}
