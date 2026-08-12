package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.*
import com.devuloopers.knet.ui.desktop.codeeditor.component.viewport.LazyCodeBodyContent
import com.devuloopers.knet.ui.desktop.codeeditor.gesture.rememberSelectionGestureHandler
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.modifier.editorPointerInput
import com.devuloopers.knet.ui.desktop.codeeditor.shortcut.EditorShortcutHandler
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.CodeHighlighterRegistry
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * Top-level code editor viewport container composable.
 *
 * Coordinates 60 FPS auto-scrolling, pointer input gestures, custom context menus, vertical scrollbar integration,
 * and delegates virtualized line list rendering to [LazyCodeBodyContent].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyCodeBody(
    rawLines: List<String>,
    mode: LazyCodeBodyMode = LazyCodeBodyMode.ReadOnly,
    foldRegions: List<FoldRegion> = emptyList(),
    collapsedFoldStartLines: Set<Int> = emptySet(),
    onToggleFold: (originalLineIndex: Int) -> Unit = {},
    isFoldingEnabled: Boolean = true,
    isWordWrapEnabled: Boolean = true,
    languageHint: String? = null,
    fontSize: TextUnit = CodeEditorTokens.FontSize,
    lineHeight: TextUnit = CodeEditorTokens.LineHeight,
    onDocumentLinesChanged: ((List<String>) -> Unit)? = null,
    onLineChanged: ((lineIndex: Int, newText: String) -> Unit)? = null,
    onLineSplit: ((lineIndex: Int, colIndex: Int) -> Unit)? = null,
    onLineMerge: ((lineIndex: Int) -> Unit)? = null,
    onMultiLinePaste: ((lineIndex: Int, caretCol: Int, pastedText: String) -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    caretState: EditorCaretState? = null,
    onCaretStateChange: ((EditorCaretState) -> Unit)? = null,
    selection: EditorSelection? = null,
    onSelectionChange: ((EditorSelection?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()

    // Auto-scroll when caret moves to an out-of-viewport line
    LaunchedEffect(caretState?.lineIndex) {
        caretState?.lineIndex?.let { targetLine ->
            if (targetLine in rawLines.indices) {
                val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                val isVisible = visibleItems.any { it.key == targetLine }
                if (!isVisible) {
                    lazyListState.animateScrollToItem(targetLine)
                }
            }
        }
    }

    val visibleLines: List<LazyLine> = remember(rawLines, foldRegions, collapsedFoldStartLines, isFoldingEnabled) {
        if (isFoldingEnabled && foldRegions.isNotEmpty()) {
            LazyLineVisibilityEngine.buildVisibleLines(rawLines, foldRegions, collapsedFoldStartLines)
        } else {
            rawLines.mapIndexed { index, text -> LazyLine(index, text, LineFoldState.None) }
        }
    }

    val maxDigits = remember(rawLines.size) {
        rawLines.size.toString().length.coerceAtLeast(3)
    }
    val gutterWidthDp = remember(maxDigits) {
        (maxDigits * 8 + 12).dp
    }

    val highlighter = remember(languageHint) {
        languageHint?.let { CodeHighlighterRegistry.resolveByLanguage(it) }
    }

    val scrollbarStyle = remember {
        ScrollbarStyle(
            minimalHeight = 24.dp,
            thickness = 8.dp,
            shape = RoundedCornerShape(4.dp),
            hoverDurationMillis = 150,
            unhoverColor = EditorColors.BorderDark.copy(alpha = 0.5f),
            hoverColor = EditorColors.ActiveBlue
        )
    }

    val copyAction = rememberClipboardCopyAction()
    val pasteAction = rememberClipboardPasteAction()
    val density = LocalDensity.current
    val lineHeightPx = remember(density, lineHeight) { with(density) { CodeEditorTokens.GutterLineHeightDp.toPx() } }
    val charWidthPx = remember(density, fontSize) { with(density) { (fontSize.value * 0.6f).sp.toPx() } }
    val totalGutterWidthDp = remember(gutterWidthDp, isFoldingEnabled) {
        if (isFoldingEnabled) {
            16.dp + 4.dp + gutterWidthDp + CodeEditorTokens.GutterPaddingEnd
        } else {
            gutterWidthDp + CodeEditorTokens.GutterPaddingEnd
        }
    }
    val gutterWidthPx = remember(density, totalGutterWidthDp) { with(density) { totalGutterWidthDp.toPx() } }
    val autoScrollThresholdPx = remember(density) { with(density) { CodeEditorTokens.AutoScrollActivationZone.toPx() } }
    val autoScrollController = rememberAutoScrollController()
    val selectionGestureHandler = rememberSelectionGestureHandler()
    var containerHeightPx by remember { mutableStateOf(0f) }
    var containerWidthPx by remember { mutableStateOf(0f) }

    val internalSelectionState = remember(rawLines) { mutableStateOf<EditorSelection?>(null) }
    val effectiveSelection = selection ?: internalSelectionState.value
    val currentSelectionState by rememberUpdatedState(effectiveSelection)
    val currentCaretState by rememberUpdatedState(caretState)
    val lineTextLayoutMap = remember { mutableMapOf<Int, TextLayoutResult>() }

    val updateSelection: (EditorSelection?) -> Unit = { newSel ->
        internalSelectionState.value = newSel
        onSelectionChange?.invoke(newSel)
    }

    val contextMenuItems = rememberEditorContextMenuItems(
        rawLines = rawLines,
        effectiveSelection = effectiveSelection,
        foldRegions = foldRegions,
        collapsedFoldStartLines = collapsedFoldStartLines,
        mode = mode,
        copyAction = copyAction,
        pasteAction = pasteAction,
        onDocumentLinesChanged = onDocumentLinesChanged,
        onSelectionChange = updateSelection
    )

    CompositionLocalProvider(LocalTextContextMenu provides EmptyTextContextMenu) {
        KNetContextMenuArea(
            items = contextMenuItems,
            modifier = modifier.fillMaxSize()
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorColors.BackgroundDark)
                .onSizeChanged { size ->
                    containerWidthPx = size.width.toFloat()
                    containerHeightPx = size.height.toFloat()
                }
                .editorPointerInput(
                    rawLines = rawLines,
                    visibleLines = visibleLines,
                    foldRegions = foldRegions,
                    collapsedFoldStartLines = collapsedFoldStartLines,
                    mode = mode,
                    containerHeightPx = containerHeightPx,
                    containerWidthPx = containerWidthPx,
                    gutterWidthPx = gutterWidthPx,
                    lineHeightPx = lineHeightPx,
                    charWidthPx = charWidthPx,
                    autoScrollThresholdPx = autoScrollThresholdPx,
                    lazyListState = lazyListState,
                    lineTextLayoutMap = lineTextLayoutMap,
                    selectionGestureHandler = selectionGestureHandler,
                    autoScrollController = autoScrollController,
                    currentSelectionState = currentSelectionState,
                    currentCaretState = currentCaretState,
                    updateSelection = updateSelection
                )
                .pointerHoverIcon(PointerIcon.Text)
                .onPreviewKeyEvent { keyEvent ->
                    EditorShortcutHandler.processKeyEvent(
                        keyEvent = keyEvent,
                        rawLines = rawLines,
                        selection = effectiveSelection,
                        caretState = caretState,
                        foldRegions = foldRegions,
                        collapsedFoldStartLines = collapsedFoldStartLines,
                        copyAction = copyAction,
                        pasteAction = pasteAction,
                        onDocumentLinesChanged = { updatedLines: List<String> ->
                            if (onDocumentLinesChanged != null) {
                                onDocumentLinesChanged(updatedLines)
                            } else if (updatedLines.isNotEmpty()) {
                                onLineChanged?.invoke(0, updatedLines[0])
                            }
                        },
                        onSelectionChange = updateSelection,
                        onCaretStateChange = { newCaret: EditorCaretState -> onCaretStateChange?.invoke(newCaret) },
                        onUndo = onUndo,
                        onRedo = onRedo
                    )
                }
        ) {
            LazyCodeBodyContent(
                visibleLines = visibleLines,
                rawLines = rawLines,
                mode = mode,
                highlighter = highlighter,
                isFoldingEnabled = isFoldingEnabled,
                isWordWrapEnabled = isWordWrapEnabled,
                gutterWidthDp = gutterWidthDp,
                fontSize = fontSize,
                lineHeight = lineHeight,
                lazyListState = lazyListState,
                caretState = caretState,
                onCaretStateChange = onCaretStateChange,
                selection = effectiveSelection,
                onToggleFold = onToggleFold,
                onLineChanged = onLineChanged,
                onLineSplit = onLineSplit,
                onLineMerge = onLineMerge,
                onMultiLinePaste = onMultiLinePaste,
                onUndo = onUndo,
                onRedo = onRedo,
                onTextLayout = { lineIdx, layoutResult ->
                    lineTextLayoutMap[lineIdx] = layoutResult
                }
            )

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(lazyListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .pointerHoverIcon(PointerIcon.Default),
                style = scrollbarStyle
            )
        }
    }
}
}
