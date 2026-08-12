package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.desktop.codeeditor.model.LineSelectionBounds
import com.devuloopers.knet.ui.desktop.codeeditor.modifier.selectionHighlight
import com.devuloopers.knet.ui.desktop.codeeditor.shortcut.LineKeyNavigationHandler
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * Editable single-line content composable for [com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody].
 *
 * Renders a single [BasicTextField] scoped to one document line. Supports syntax highlighting via
 * [VisualTransformation], multi-line paste interception, focus synchronization, and cross-line
 * keyboard navigation (Up, Down, Left at start, Right at end, Backspace at start, Enter).
 */
@Composable
fun EditableLineContent(
    lineIndex: Int,
    lineText: String,
    highlightedText: AnnotatedString? = null,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    isWordWrapEnabled: Boolean = true,
    lineSelectionBounds: LineSelectionBounds? = null,
    isViewportSelecting: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    isActive: Boolean = false,
    targetColIndex: Int? = null,
    onTextChanged: (newText: String) -> Unit,
    onLineSplit: (colIndex: Int) -> Unit,
    onLineMerge: () -> Unit,
    onMultiLinePaste: (caretCol: Int, pastedText: String) -> Unit = { _, _ -> },
    onNavigateUp: (targetCol: Int) -> Unit = {},
    onNavigateDown: (targetCol: Int) -> Unit = {},
    onNavigateLeftAtStart: () -> Unit = {},
    onNavigateRightAtEnd: () -> Unit = {},
    onFocused: (caretCol: Int) -> Unit = {},
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Stable initialization: do NOT reset selection to end-of-line on every lineText change.
    // Selection is managed explicitly by LaunchedEffect below.
    var fieldValue by remember { mutableStateOf(TextFieldValue(text = lineText, selection = TextRange(0))) }

    // Collapse native BasicTextField selection when viewport selection is active (prevents dual selection)
    LaunchedEffect(isViewportSelecting, lineSelectionBounds) {
        if ((isViewportSelecting || lineSelectionBounds != null) && !fieldValue.selection.collapsed) {
            fieldValue = fieldValue.copy(selection = TextRange(fieldValue.selection.start))
        }
    }

    // When lineText changes externally (undo, redo, backspace selection delete, paste):
    // clamp the existing cursor position to the new text length rather than resetting to end-of-line.
    LaunchedEffect(lineText) {
        if (fieldValue.text != lineText) {
            val clampedCol = fieldValue.selection.start.coerceIn(0, lineText.length)
            fieldValue = TextFieldValue(text = lineText, selection = TextRange(clampedCol))
        }
    }

    // Explicit navigation or undo/redo caret restoration: set cursor to targetColIndex exactly.
    // Runs only when the parent explicitly sets a targetColIndex (arrow keys, undo, redo, backspace delete).
    LaunchedEffect(isActive, targetColIndex) {
        if (isActive && targetColIndex != null) {
            val safeCol = targetColIndex.coerceIn(0, lineText.length)
            fieldValue = TextFieldValue(text = lineText, selection = TextRange(safeCol))
            try {
                focusRequester.requestFocus()
            } catch (_: Throwable) {
            }
        }
    }


    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val visualTransformation = remember(highlightedText) {
        VisualTransformation { input ->
            // Guard against stale highlightedText: if its length differs from the current input,
            // the OffsetMapping.Identity mapping would produce an out-of-range crash.
            // Only use highlightedText when it matches the current input length exactly.
            val annotated = if (highlightedText != null && highlightedText.length == input.text.length) {
                highlightedText
            } else {
                AnnotatedString(input.text)
            }
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }

    val transparentSelectionColors = remember {
        TextSelectionColors(
            handleColor = Color.Transparent,
            backgroundColor = Color.Transparent
        )
    }

    val activeCursorBrush = remember(isViewportSelecting, lineSelectionBounds) {
        if (isViewportSelecting || lineSelectionBounds != null) {
            SolidColor(Color.Transparent)
        } else {
            SolidColor(EditorColors.ActiveBlue)
        }
    }

    CompositionLocalProvider(LocalTextSelectionColors provides transparentSelectionColors) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { updated ->
                val pasteCheck = updated.text.length - fieldValue.text.length
                val isPasteOperation = pasteCheck > 1 && updated.text.contains("\n")

                if (isPasteOperation) {
                    val caretCol = fieldValue.selection.start
                    onMultiLinePaste(caretCol, updated.text)
                } else {
                    val textChanged = updated.text != fieldValue.text
                    val selectionChanged = updated.selection != fieldValue.selection
                    fieldValue = updated
                    if (textChanged) {
                        onTextChanged(updated.text)
                    }
                    if (selectionChanged || textChanged) {
                        onFocused(updated.selection.start)
                    }
                }
            },

            onTextLayout = {
                textLayoutResult = it
                onTextLayout?.invoke(it)
            },
            singleLine = !isWordWrapEnabled,
            visualTransformation = visualTransformation,
            cursorBrush = activeCursorBrush,

            textStyle = CodeEditorTokens.editorTextStyle(
                fontSize = fontSize,
                lineHeight = lineHeight
            ).copy(
                color = Color.White,
                fontFamily = FontFamily.Monospace
            ),
            modifier = modifier
                .fillMaxWidth()
                .selectionHighlight(lineSelectionBounds, textLayoutResult, fontSize)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        onFocused(fieldValue.selection.start)
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    LineKeyNavigationHandler.handleLineKeyEvent(
                        keyEvent = keyEvent,
                        caretCol = fieldValue.selection.start,
                        isCollapsed = fieldValue.selection.collapsed,
                        textLength = fieldValue.text.length,
                        onNavigateUp = onNavigateUp,
                        onNavigateDown = onNavigateDown,
                        onNavigateLeftAtStart = onNavigateLeftAtStart,
                        onNavigateRightAtEnd = onNavigateRightAtEnd,
                        onLineMerge = onLineMerge,
                        onLineSplit = onLineSplit,
                        onUndo = onUndo,
                        onRedo = onRedo
                    )
                }
        )
    }
}

