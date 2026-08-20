package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBodyMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorStrings
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorTokenizedDocument
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorSemanticColors
import com.devuloopers.knet.ui.desktop.codeeditor.viewport.EditorVisualLineMap

/**
 * Virtualized line renderer backed by immutable document and visual-line mappings.
 */
@Composable
internal fun LazyCodeBodyContent(
    snapshot: EditorDocumentSnapshot,
    visualLineMap: EditorVisualLineMap,
    mode: LazyCodeBodyMode,
    tokenizedDocument: EditorTokenizedDocument?,
    searchMatchesByLine: Map<Int, List<EditorRange>>,
    activeSearchMatch: EditorRange?,
    semanticColors: CodeEditorSemanticColors,
    strings: CodeEditorStrings,
    isFoldingEnabled: Boolean,
    isWordWrapEnabled: Boolean,
    gutterWidthDp: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    lazyListState: LazyListState,
    horizontalScrollState: ScrollState,
    caret: EditorPosition,
    onCaretChange: (EditorPosition) -> Unit,
    selection: EditorSelection?,
    isSelectionGestureActive: Boolean,
    isSelectionGestureActiveNow: () -> Boolean,
    onLineInputFocused: () -> Unit,
    shouldRequestEditorFocus: Boolean,
    onToggleFold: (originalLineIndex: Int) -> Unit,
    onLineChanged: ((lineIndex: Int, newText: String) -> Unit)?,
    onLineSplit: ((lineIndex: Int, colIndex: Int) -> Unit)?,
    onLineMerge: ((lineIndex: Int) -> Unit)?,
    onMultiLinePaste: ((lineIndex: Int, caretCol: Int, pastedText: String) -> Unit)?,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    onTextLayout: ((lineIndex: Int, TextLayoutResult?) -> Unit)? = null
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Text)
    ) {
        items(
            count = visualLineMap.visibleLineCount,
            key = { visualIndex -> visualLineMap.toDocumentLine(visualIndex) }
        ) { visualIndex ->
            val line = visualLineMap.lazyLine(snapshot, visualIndex)
            val lineIndex = line.originalLineIndex
            val isActive = caret.line == lineIndex
            val focusRequester = remember(lineIndex) { FocusRequester() }
            val tokens = tokenizedDocument
                ?.takeIf { it.snapshot.version == snapshot.version }
                ?.tokensForLine(lineIndex)
                .orEmpty()

            LazyCodeLineRow(
                line = line,
                mode = mode,
                semanticTokens = tokens,
                searchMatches = searchMatchesByLine[lineIndex].orEmpty(),
                activeSearchMatch = activeSearchMatch,
                semanticColors = semanticColors,
                strings = strings,
                isFoldingEnabled = isFoldingEnabled,
                isWordWrapEnabled = isWordWrapEnabled,
                gutterWidthDp = gutterWidthDp,
                fontSize = fontSize,
                lineHeight = lineHeight,
                horizontalScrollState = horizontalScrollState,
                isActiveLine = isActive,
                selection = selection,
                isSelectionGestureActive = isSelectionGestureActive,
                isSelectionGestureActiveNow = isSelectionGestureActiveNow,
                onLineInputFocused = onLineInputFocused,
                shouldRequestEditorFocus = shouldRequestEditorFocus,
                targetColIndex = if (isActive) caret.column else null,
                focusRequester = focusRequester,
                onToggleFold = onToggleFold,
                onLineChanged = onLineChanged,
                onLineSplit = onLineSplit,
                onLineMerge = onLineMerge,
                onMultiLinePaste = onMultiLinePaste,
                onNavigateUp = { originalLine, column ->
                    if (originalLine > 0) onCaretChange(EditorPosition(originalLine - 1, column))
                },
                onNavigateDown = { originalLine, column ->
                    if (originalLine < snapshot.lineCount - 1) {
                        onCaretChange(EditorPosition(originalLine + 1, column))
                    }
                },
                onNavigateLeftAtStart = { originalLine ->
                    if (originalLine > 0) {
                        onCaretChange(
                            EditorPosition(originalLine - 1, snapshot.line(originalLine - 1).length)
                        )
                    }
                },
                onNavigateRightAtEnd = { originalLine ->
                    if (originalLine < snapshot.lineCount - 1) {
                        onCaretChange(EditorPosition(originalLine + 1, 0))
                    }
                },
                onFocused = { originalLine, clickedColumn ->
                    onCaretChange(EditorPosition(originalLine, clickedColumn))
                },
                onUndo = onUndo,
                onRedo = onRedo,
                onTextLayout = onTextLayout
            )
        }
    }
}
