package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LazyLine
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBodyMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorStrings
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorToken
import com.devuloopers.knet.ui.desktop.codeeditor.render.SemanticTokenRenderer
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorSemanticColors
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/** Renders one virtualized logical document line. */
@Composable
internal fun LazyCodeLineRow(
    line: LazyLine,
    mode: LazyCodeBodyMode,
    semanticTokens: List<EditorToken>,
    searchMatches: List<EditorRange>,
    activeSearchMatch: EditorRange?,
    semanticColors: CodeEditorSemanticColors,
    strings: CodeEditorStrings,
    isFoldingEnabled: Boolean,
    isWordWrapEnabled: Boolean,
    gutterWidthDp: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    horizontalScrollState: ScrollState,
    isActiveLine: Boolean = false,
    selection: EditorSelection? = null,
    isSelectionGestureActive: Boolean = false,
    isSelectionGestureActiveNow: () -> Boolean,
    onLineInputFocused: () -> Unit,
    shouldRequestEditorFocus: Boolean,
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
    onTextLayout: ((originalLineIndex: Int, TextLayoutResult?) -> Unit)? = null
) {
    val highlightedText: AnnotatedString = remember(
        line.displayText,
        semanticTokens,
        searchMatches,
        activeSearchMatch,
        semanticColors
    ) {
        SemanticTokenRenderer.renderLine(
            lineText = line.displayText,
            tokens = semanticTokens,
            searchMatches = searchMatches,
            activeSearchMatch = activeSearchMatch,
            colors = semanticColors
        )
    }
    val lineSelectionBounds = remember(selection, line.originalLineIndex, line.displayText) {
        selection.boundsForLine(line.originalLineIndex, line.displayText.length)
    }
    val hasViewportSelection = remember(selection) { selection != null && !selection.isEmpty }
    val isViewportSelecting = isSelectionGestureActive || hasViewportSelection
    var measuredTextLayout by remember(line.originalLineIndex) {
        mutableStateOf<TextLayoutResult?>(null)
    }
    val compatibleTextLayout = measuredTextLayout?.takeIf {
        it.layoutInput.text.text == line.displayText
    }
    val currentOnTextLayout by rememberUpdatedState(onTextLayout)

    DisposableEffect(line.originalLineIndex) {
        onDispose { currentOnTextLayout?.invoke(line.originalLineIndex, null) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActiveLine) {
                    Modifier.background(EditorColors.ActiveLineBackground)
                }
                else Modifier
            )
            .then(if (!isWordWrapEnabled) Modifier.height(CodeEditorTokens.GutterLineHeightDp) else Modifier),
        verticalAlignment = if (isWordWrapEnabled) Alignment.Top else Alignment.CenterVertically
    ) {
        LazyCodeGutterSlot(
            displayLineNumber = line.originalLineIndex + 1,
            foldState = line.foldState,
            isFoldingEnabled = isFoldingEnabled,
            gutterWidthDp = gutterWidthDp,
            fontSize = fontSize,
            lineHeight = lineHeight,
            strings = strings,
            onToggleFold = { onToggleFold(line.originalLineIndex) }
        )

        if (mode == LazyCodeBodyMode.Editable && isActiveLine) {
            EditableLineContent(
                lineIndex = line.originalLineIndex,
                lineText = line.displayText,
                highlightedText = highlightedText,
                fontSize = fontSize,
                lineHeight = lineHeight,
                isWordWrapEnabled = isWordWrapEnabled,
                lineSelectionBounds = lineSelectionBounds,
                isViewportSelecting = isViewportSelecting,
                isSelectionGestureActiveNow = isSelectionGestureActiveNow,
                onInputFocused = onLineInputFocused,
                focusRequester = focusRequester,
                isActive = true,
                shouldRequestFocus = shouldRequestEditorFocus,
                targetColIndex = targetColIndex,
                textLayoutResult = compatibleTextLayout,
                onTextLayout = {
                    measuredTextLayout = it
                    currentOnTextLayout?.invoke(line.originalLineIndex, it)
                },
                onTextChanged = { onLineChanged?.invoke(line.originalLineIndex, it) },
                onLineSplit = { onLineSplit?.invoke(line.originalLineIndex, it) },
                onLineMerge = { onLineMerge?.invoke(line.originalLineIndex) },
                onMultiLinePaste = { column, text ->
                    onMultiLinePaste?.invoke(line.originalLineIndex, column, text)
                },
                onNavigateUp = { onNavigateUp?.invoke(line.originalLineIndex, it) },
                onNavigateDown = { onNavigateDown?.invoke(line.originalLineIndex, it) },
                onNavigateLeftAtStart = { onNavigateLeftAtStart?.invoke(line.originalLineIndex) },
                onNavigateRightAtEnd = { onNavigateRightAtEnd?.invoke(line.originalLineIndex) },
                onFocused = { onFocused?.invoke(line.originalLineIndex, it) },
                onUndo = onUndo,
                onRedo = onRedo,
                modifier = Modifier.weight(1f)
            )
        } else {
            ReadOnlyLineContent(
                highlightedText = highlightedText,
                isWordWrapEnabled = isWordWrapEnabled,
                fontSize = fontSize,
                lineHeight = lineHeight,
                horizontalScrollState = horizontalScrollState,
                lineSelectionBounds = lineSelectionBounds,
                textLayoutResult = compatibleTextLayout,
                onTextLayout = {
                    measuredTextLayout = it
                    currentOnTextLayout?.invoke(line.originalLineIndex, it)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Keeps editable and read-only content aligned with the gutter while allowing wrapped rows to grow. */
internal fun Modifier.editorLineContentHeight(isWordWrapEnabled: Boolean): Modifier {
    return heightIn(min = CodeEditorTokens.GutterLineHeightDp)
        .then(if (!isWordWrapEnabled) Modifier.fillMaxHeight() else Modifier)
}
