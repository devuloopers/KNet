package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
internal fun EditableLineContent(
    lineIndex: Int,
    lineText: String,
    highlightedText: AnnotatedString? = null,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    isWordWrapEnabled: Boolean = true,
    lineSelectionBounds: LineSelectionBounds? = null,
    isViewportSelecting: Boolean = false,
    isSelectionGestureActiveNow: () -> Boolean = { false },
    onInputFocused: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    isActive: Boolean = false,
    shouldRequestFocus: Boolean,
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
    textLayoutResult: TextLayoutResult? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(text = lineText, selection = TextRange(0))) }

    // Synchronously align fieldValue during composition when lineText changes externally (Op Name sync, paste, undo).
    // Eliminates 1-frame asynchronous LaunchedEffect lag and visual flash in virtualized LazyColumn.
    if (fieldValue.text != lineText) {
        val clampedCol = fieldValue.selection.start.coerceIn(0, lineText.length)
        fieldValue = TextFieldValue(text = lineText, selection = TextRange(clampedCol))
    }

    // Collapse native BasicTextField selection when viewport selection is active (prevents dual selection)
    LaunchedEffect(isViewportSelecting, lineSelectionBounds) {
        if ((isViewportSelecting || lineSelectionBounds != null) && !fieldValue.selection.collapsed) {
            fieldValue = fieldValue.copy(selection = TextRange(fieldValue.selection.start))
        }
    }

    // Explicit navigation or undo/redo caret restoration: set cursor to targetColIndex exactly.
    // Runs only when the parent explicitly sets a targetColIndex (arrow keys, undo, redo, backspace delete).
    LaunchedEffect(isActive, targetColIndex, shouldRequestFocus, isViewportSelecting) {
        if (isActive && targetColIndex != null) {
            val safeCol = targetColIndex.coerceIn(0, lineText.length)
            fieldValue = TextFieldValue(text = lineText, selection = TextRange(safeCol))
            if (shouldRequestLineInputFocus(isActive, targetColIndex, shouldRequestFocus, isViewportSelecting)) {
                try {
                    focusRequester.requestFocus()
                } catch (_: Throwable) {
                }
            }
        }
    }

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
        Box(
            modifier = modifier
                .fillMaxWidth()
                .editorLineContentHeight(isWordWrapEnabled)
                .selectionHighlight(lineSelectionBounds, textLayoutResult, fontSize),
            contentAlignment = if (isWordWrapEnabled) Alignment.TopStart else Alignment.CenterStart
        ) {
            BasicTextField(
                value = fieldValue,
                onValueChange = { updated ->
                    val pasteCheck = updated.text.length - fieldValue.text.length
                    val isPasteOperation = pasteCheck > 1 && updated.text.contains("\n")

                    if (isPasteOperation) {
                        var commonPrefix = 0
                        val commonLimit = minOf(fieldValue.text.length, updated.text.length)
                        while (
                            commonPrefix < commonLimit &&
                            fieldValue.text[commonPrefix] == updated.text[commonPrefix]
                        ) {
                            commonPrefix++
                        }
                        var commonSuffix = 0
                        while (
                            commonSuffix < fieldValue.text.length - commonPrefix &&
                            commonSuffix < updated.text.length - commonPrefix &&
                            fieldValue.text[fieldValue.text.lastIndex - commonSuffix] ==
                            updated.text[updated.text.lastIndex - commonSuffix]
                        ) {
                            commonSuffix++
                        }
                        val insertedEnd = updated.text.length - commonSuffix
                        onMultiLinePaste(commonPrefix, updated.text.substring(commonPrefix, insertedEnd))
                    } else {
                        val textChanged = updated.text != fieldValue.text
                        val selectionChanged = updated.selection != fieldValue.selection
                        fieldValue = updated
                        if (textChanged) {
                            onTextChanged(updated.text)
                        }
                        if (
                            (selectionChanged || textChanged) &&
                            shouldPublishLineInputCaret(
                                isFocused = true,
                                isViewportSelecting = isViewportSelecting,
                                isSelectionGestureActive = isSelectionGestureActiveNow()
                            )
                        ) {
                            onFocused(updated.selection.start)
                        }
                    }
                },

                onTextLayout = { onTextLayout?.invoke(it) },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) onInputFocused()
                        if (
                            shouldPublishLineInputCaret(
                                isFocused = state.isFocused,
                                isViewportSelecting = isViewportSelecting,
                                isSelectionGestureActive = isSelectionGestureActiveNow()
                            )
                        ) {
                            onFocused(fieldValue.selection.start)
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        LineKeyNavigationHandler.handleLineKeyEvent(
                            keyEvent = keyEvent,
                            caretCol = fieldValue.selection.start,
                            isCollapsed = fieldValue.selection.collapsed,
                            textLength = fieldValue.text.length,
                            isWordWrapEnabled = isWordWrapEnabled,
                            currentVisualLineIndex = textLayoutResult?.getLineForOffset(
                                fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
                            ),
                            visualLineCount = textLayoutResult?.lineCount ?: 1,
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
}

/** Returns whether a newly active line input may request focus without disturbing viewport selection. */
internal fun shouldRequestLineInputFocus(
    isActive: Boolean,
    targetColumn: Int?,
    shouldRequestFocus: Boolean,
    isViewportSelecting: Boolean
): Boolean {
    return isActive && targetColumn != null && shouldRequestFocus && !isViewportSelecting
}

/** Returns whether a focus event may publish a caret update to the editor session. */
internal fun shouldPublishLineInputCaret(
    isFocused: Boolean,
    isViewportSelecting: Boolean,
    isSelectionGestureActive: Boolean
): Boolean {
    return isFocused && !isViewportSelecting && !isSelectionGestureActive
}
