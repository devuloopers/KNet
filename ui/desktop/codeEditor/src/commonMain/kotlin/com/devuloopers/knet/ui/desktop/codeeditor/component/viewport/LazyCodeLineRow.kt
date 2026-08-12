package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LazyLine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.SelectionEngine
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBodyMode
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.CodeLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.TokenMaker
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.TokenState
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * Top-level row composable for a single document line inside
 * [com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody]'s [LazyColumn].
 */
@Composable
fun LazyCodeLineRow(
    line: LazyLine,
    mode: LazyCodeBodyMode,
    highlighter: CodeLanguageHighlighter?,
    isFoldingEnabled: Boolean,
    isWordWrapEnabled: Boolean,
    gutterWidthDp: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    isActiveLine: Boolean = false,
    selection: EditorSelection? = null,
    targetColIndex: Int? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onToggleFold: (originalLineIndex: Int) -> Unit,
    onLineChanged: ((lineIndex: Int, newText: String) -> Unit)? = null,
    onLineSplit: ((lineIndex: Int, colIndex: Int) -> Unit)? = null,
    onLineMerge: ((lineIndex: Int) -> Unit)? = null,
    onMultiLinePaste: ((lineIndex: Int, caretCol: Int, pastedText: String) -> Unit)? = null,
    onNavigateUp: ((lineIndex: Int, col: Int) -> Unit)? = null,
    onNavigateDown: ((lineIndex: Int, col: Int) -> Unit)? = null,
    onNavigateLeftAtStart: ((lineIndex: Int) -> Unit)? = null,
    onNavigateRightAtEnd: ((lineIndex: Int) -> Unit)? = null,
    onFocused: ((lineIndex: Int, caretCol: Int) -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    onTextLayout: ((originalLineIndex: Int, TextLayoutResult) -> Unit)? = null
) {
    val highlightedText: AnnotatedString = remember(line.displayText, highlighter) {
        highlighter?.highlightLine(line.displayText)
            ?: TokenMaker.tokenizeLine(line.displayText, TokenState.NULL).annotatedString
    }

    val lineSelectionBounds = remember(selection, line.originalLineIndex, line.displayText) {
        SelectionEngine.computeLineBounds(selection, line.originalLineIndex, line.displayText.length)
    }

    val isViewportSelecting = remember(selection) {
        selection != null && !selection.isEmpty
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActiveLine && !isViewportSelecting) Modifier.background(EditorColors.ActiveLineBackground)
                else Modifier
            )
            .then(
                if (!isWordWrapEnabled) Modifier.height(CodeEditorTokens.GutterLineHeightDp)
                else Modifier
            ),
        verticalAlignment = if (isWordWrapEnabled) Alignment.Top else Alignment.CenterVertically
    ) {
        LazyCodeGutterSlot(
            displayLineNumber = line.originalLineIndex + 1,
            foldState = line.foldState,
            isFoldingEnabled = isFoldingEnabled,
            gutterWidthDp = gutterWidthDp,
            fontSize = fontSize,
            lineHeight = lineHeight,
            onToggleFold = { onToggleFold(line.originalLineIndex) }
        )

        when (mode) {
            LazyCodeBodyMode.ReadOnly -> {
                ReadOnlyLineContent(
                    highlightedText = highlightedText,
                    isWordWrapEnabled = isWordWrapEnabled,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    lineSelectionBounds = lineSelectionBounds,
                    onTextLayout = { layoutResult ->
                        onTextLayout?.invoke(line.originalLineIndex, layoutResult)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            LazyCodeBodyMode.Editable -> {
                EditableLineContent(
                    lineIndex = line.originalLineIndex,
                    lineText = line.displayText,
                    highlightedText = highlightedText,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    isWordWrapEnabled = isWordWrapEnabled,
                    lineSelectionBounds = lineSelectionBounds,
                    isViewportSelecting = isViewportSelecting,
                    focusRequester = focusRequester,
                    isActive = isActiveLine,
                    targetColIndex = targetColIndex,
                    onTextLayout = { layoutResult ->
                        onTextLayout?.invoke(line.originalLineIndex, layoutResult)
                    },
                    onTextChanged = { newText ->
                        onLineChanged?.invoke(line.originalLineIndex, newText)
                    },
                    onLineSplit = { colIndex ->
                        onLineSplit?.invoke(line.originalLineIndex, colIndex)
                    },
                    onLineMerge = {
                        onLineMerge?.invoke(line.originalLineIndex)
                    },
                    onMultiLinePaste = { caretCol, pastedText ->
                        onMultiLinePaste?.invoke(line.originalLineIndex, caretCol, pastedText)
                    },
                    onNavigateUp = { col ->
                        onNavigateUp?.invoke(line.originalLineIndex, col)
                    },
                    onNavigateDown = { col ->
                        onNavigateDown?.invoke(line.originalLineIndex, col)
                    },
                    onNavigateLeftAtStart = {
                        onNavigateLeftAtStart?.invoke(line.originalLineIndex)
                    },
                    onNavigateRightAtEnd = {
                        onNavigateRightAtEnd?.invoke(line.originalLineIndex)
                    },
                    onFocused = { caretCol ->
                        onFocused?.invoke(line.originalLineIndex, caretCol)
                    },
                    onUndo = onUndo,
                    onRedo = onRedo,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
