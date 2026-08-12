package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LazyLine
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBodyMode
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.CodeLanguageHighlighter

/**
 * Internal virtualized line list renderer for [com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody].
 *
 * Renders document lines efficiently inside a [LazyColumn], managing line-level focus requesters and caret column
 * target propagation.
 */
@Composable
fun LazyCodeBodyContent(
    visibleLines: List<LazyLine>,
    rawLines: List<String>,
    mode: LazyCodeBodyMode,
    highlighter: CodeLanguageHighlighter?,
    isFoldingEnabled: Boolean,
    isWordWrapEnabled: Boolean,
    gutterWidthDp: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    lazyListState: LazyListState,
    caretState: EditorCaretState?,
    onCaretStateChange: ((EditorCaretState) -> Unit)?,
    selection: EditorSelection?,
    onToggleFold: (originalLineIndex: Int) -> Unit,
    onLineChanged: ((lineIndex: Int, newText: String) -> Unit)?,
    onLineSplit: ((lineIndex: Int, colIndex: Int) -> Unit)?,
    onLineMerge: ((lineIndex: Int) -> Unit)?,
    onMultiLinePaste: ((lineIndex: Int, caretCol: Int, pastedText: String) -> Unit)?,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    onTextLayout: ((lineIndex: Int, TextLayoutResult) -> Unit)? = null
) {
    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Text)
    ) {
        itemsIndexed(
            items = visibleLines,
            key = { _, line -> line.originalLineIndex }
        ) { _, line ->
            val lineIdx = line.originalLineIndex
            val isActive = (caretState?.lineIndex == lineIdx)
            val focusRequester = focusRequesters.getOrPut(lineIdx) { FocusRequester() }

            LazyCodeLineRow(
                line = line,
                mode = mode,
                highlighter = highlighter,
                isFoldingEnabled = isFoldingEnabled,
                isWordWrapEnabled = isWordWrapEnabled,
                gutterWidthDp = gutterWidthDp,
                fontSize = fontSize,
                lineHeight = lineHeight,
                isActiveLine = isActive,
                selection = selection,
                targetColIndex = if (isActive) caretState.colIndex else null,
                focusRequester = focusRequester,
                onToggleFold = onToggleFold,
                onLineChanged = onLineChanged,
                onLineSplit = onLineSplit,
                onLineMerge = onLineMerge,
                onMultiLinePaste = onMultiLinePaste,
                onNavigateUp = { origIdx, col ->
                    if (origIdx > 0) {
                        onCaretStateChange?.invoke(EditorCaretState(origIdx - 1, col))
                    }
                },
                onNavigateDown = { origIdx, col ->
                    if (origIdx < rawLines.lastIndex) {
                        onCaretStateChange?.invoke(EditorCaretState(origIdx + 1, col))
                    }
                },
                onNavigateLeftAtStart = { origIdx ->
                    if (origIdx > 0) {
                        val prevLen = rawLines[origIdx - 1].length
                        onCaretStateChange?.invoke(EditorCaretState(origIdx - 1, prevLen))
                    }
                },
                onNavigateRightAtEnd = { origIdx ->
                    if (origIdx < rawLines.lastIndex) {
                        onCaretStateChange?.invoke(EditorCaretState(origIdx + 1, 0))
                    }
                },
                onFocused = { origIdx, clickedCol ->
                    onCaretStateChange?.invoke(EditorCaretState(origIdx, clickedCol))
                },
                onUndo = onUndo,
                onRedo = onRedo,
                onTextLayout = onTextLayout
            )
        }
    }
}
