package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.components.menu.KNetContextMenuArea
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorStrings
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.rememberAutoScrollController
import com.devuloopers.knet.ui.desktop.codeeditor.component.viewport.LazyCodeBodyContent
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.gesture.rememberSelectionGestureHandler
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorTokenizedDocument
import com.devuloopers.knet.ui.desktop.codeeditor.modifier.editorPointerInput
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchResult
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorSemanticColors
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors
import com.devuloopers.knet.ui.desktop.codeeditor.viewport.EditorVisualLineMap

/**
 * Virtualized editor viewport over immutable document, language, fold, and search projections.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LazyCodeBody(
    snapshot: EditorDocumentSnapshot,
    mode: LazyCodeBodyMode,
    tokenizedDocument: EditorTokenizedDocument?,
    searchResult: EditorSearchResult?,
    activeSearchMatch: EditorRange?,
    semanticColors: CodeEditorSemanticColors,
    strings: CodeEditorStrings,
    foldRegions: List<FoldRegion>,
    collapsedFoldStartLines: Set<Int>,
    onToggleFold: (originalLineIndex: Int) -> Unit,
    isFoldingEnabled: Boolean,
    isWordWrapEnabled: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    caret: EditorPosition,
    onCaretChange: (EditorPosition) -> Unit,
    selection: EditorSelection?,
    onSelectionChange: (EditorSelection?) -> Unit,
    onDeleteSelection: () -> Unit,
    onDeleteCurrentLine: () -> Unit,
    onPaste: (String) -> Unit,
    onSelectAll: () -> Unit,
    onLineChanged: (lineIndex: Int, newText: String) -> Unit,
    onLineSplit: (lineIndex: Int, colIndex: Int) -> Unit,
    onLineMerge: (lineIndex: Int) -> Unit,
    onMultiLinePaste: (lineIndex: Int, caretCol: Int, pastedText: String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleComment: (() -> Unit)?,
    shouldRequestEditorFocus: Boolean,
    onOpenSearch: (() -> Unit)?,
    onCloseSearch: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val visualLineMap = remember(snapshot.lineCount, foldRegions, collapsedFoldStartLines, isFoldingEnabled) {
        EditorVisualLineMap.build(
            documentLineCount = snapshot.lineCount,
            foldRegions = if (isFoldingEnabled) foldRegions else emptyList(),
            collapsedStarts = if (isFoldingEnabled) collapsedFoldStartLines else emptySet()
        )
    }

    LaunchedEffect(caret.line, visualLineMap) {
        val targetVisualLine = visualLineMap.toVisibleLine(caret.line) ?: return@LaunchedEffect
        val isVisible = lazyListState.layoutInfo.visibleItemsInfo.any { it.index == targetVisualLine }
        if (!isVisible) lazyListState.animateScrollToItem(targetVisualLine)
    }

    val maximumDigits = remember(snapshot.lineCount) { snapshot.lineCount.toString().length.coerceAtLeast(3) }
    val gutterWidth = remember(maximumDigits) {
        CodeEditorTokens.GutterDigitWidth * maximumDigits + CodeEditorTokens.GutterWidthPadding
    }
    val scrollbarStyle = remember {
        ScrollbarStyle(
            minimalHeight = CodeEditorTokens.ScrollbarMinimumHeight,
            thickness = CodeEditorTokens.ScrollbarThickness,
            shape = RoundedCornerShape(CodeEditorTokens.ScrollbarCornerRadius),
            hoverDurationMillis = 150,
            unhoverColor = EditorColors.BorderDark.copy(alpha = 0.5f),
            hoverColor = EditorColors.ActiveBlue
        )
    }
    val copyAction = rememberClipboardCopyAction()
    val pasteAction = rememberClipboardPasteAction()
    val density = LocalDensity.current
    val lineHeightPixels = remember(density, lineHeight) { with(density) { lineHeight.toPx() } }
    val characterWidthPixels = remember(density, fontSize) { with(density) { (fontSize.value * 0.6f).sp.toPx() } }
    val totalGutterWidth = remember(gutterWidth, isFoldingEnabled) {
        if (isFoldingEnabled) {
            CodeEditorTokens.FoldArrowBoxSize + CodeEditorTokens.FoldArrowPaddingEnd +
                gutterWidth + CodeEditorTokens.GutterPaddingEnd
        }
        else gutterWidth + CodeEditorTokens.GutterPaddingEnd
    }
    val gutterWidthPixels = remember(density, totalGutterWidth) { with(density) { totalGutterWidth.toPx() } }
    val autoScrollThresholdPixels = remember(density) {
        with(density) { CodeEditorTokens.AutoScrollActivationZone.toPx() }
    }
    val autoScrollController = rememberAutoScrollController()
    val selectionGestureHandler = rememberSelectionGestureHandler()
    val focusRequester = remember { FocusRequester() }
    var containerHeightPixels by remember { mutableStateOf(0f) }
    var containerWidthPixels by remember { mutableStateOf(0f) }
    val currentSelection by rememberUpdatedState(selection)
    val currentCaret by rememberUpdatedState(caret)
    val lineTextLayouts = remember { mutableMapOf<Int, TextLayoutResult>() }
    val searchMatchesByLine: Map<Int, List<EditorRange>> = remember(searchResult) {
        searchResult?.matches.orEmpty().groupBy({ it.range.start.line }, { it.range })
    }
    val selectedText = remember(snapshot, selection) {
        selection?.takeUnless { it.isEmpty }?.let { snapshot.text(it.range) }.orEmpty()
    }
    val contextMenuItems = rememberEditorContextMenuItems(
        snapshot = snapshot,
        selection = selection,
        mode = mode,
        strings = strings,
        copyAction = copyAction,
        pasteAction = pasteAction,
        onDeleteSelection = onDeleteSelection,
        onPaste = onPaste,
        onSelectAll = onSelectAll
    )

    CompositionLocalProvider(LocalTextContextMenu provides EmptyTextContextMenu) {
        KNetContextMenuArea(items = contextMenuItems, modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onSizeChanged { size ->
                        containerWidthPixels = size.width.toFloat()
                        containerHeightPixels = size.height.toFloat()
                    }
                    .editorPointerInput(
                        snapshot = snapshot,
                        visualLineMap = visualLineMap,
                        containerHeightPx = containerHeightPixels,
                        containerWidthPx = containerWidthPixels,
                        gutterWidthPx = gutterWidthPixels,
                        lineHeightPx = lineHeightPixels,
                        charWidthPx = characterWidthPixels,
                        autoScrollThresholdPx = autoScrollThresholdPixels,
                        lazyListState = lazyListState,
                        lineTextLayoutMap = lineTextLayouts,
                        selectionGestureHandler = selectionGestureHandler,
                        autoScrollController = autoScrollController,
                        currentSelectionState = currentSelection,
                        currentCaret = currentCaret,
                        updateCaret = { position ->
                            onCaretChange(position)
                            runCatching { focusRequester.requestFocus() }
                        },
                        updateSelection = onSelectionChange
                    )
                    .pointerHoverIcon(PointerIcon.Text)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val commandModifier = event.isMetaPressed || event.isCtrlPressed
                        if (commandModifier) {
                            when (event.key) {
                                Key.A -> {
                                    onSelectAll()
                                    true
                                }
                                Key.C -> {
                                    if (selectedText.isNotEmpty()) copyAction(selectedText)
                                    else copyAction(snapshot.line(caret.line) + "\n")
                                    true
                                }
                                Key.X -> {
                                    if (mode == LazyCodeBodyMode.ReadOnly) return@onPreviewKeyEvent false
                                    if (selectedText.isNotEmpty()) {
                                        copyAction(selectedText)
                                        onDeleteSelection()
                                    } else {
                                        copyAction(snapshot.line(caret.line) + "\n")
                                        onDeleteCurrentLine()
                                    }
                                    true
                                }
                                Key.V -> {
                                    if (mode == LazyCodeBodyMode.ReadOnly) return@onPreviewKeyEvent false
                                    pasteAction(onPaste)
                                    true
                                }
                                Key.Z -> {
                                    if (event.isShiftPressed) onRedo() else onUndo()
                                    true
                                }
                                Key.Y -> {
                                    onRedo()
                                    true
                                }
                                Key.F -> {
                                    onOpenSearch?.invoke()
                                    onOpenSearch != null
                                }
                                Key.Slash -> {
                                    onToggleComment?.invoke()
                                    onToggleComment != null
                                }
                                else -> false
                            }
                        } else if (
                            mode == LazyCodeBodyMode.Editable &&
                            selectedText.isNotEmpty() &&
                            (event.key == Key.Backspace || event.key == Key.Delete)
                        ) {
                            onDeleteSelection()
                            true
                        } else if (event.key == Key.Escape && onCloseSearch != null) {
                            onCloseSearch()
                            true
                        } else {
                            false
                        }
                    }
            ) {
                LazyCodeBodyContent(
                    snapshot = snapshot,
                    visualLineMap = visualLineMap,
                    mode = mode,
                    tokenizedDocument = tokenizedDocument,
                    searchMatchesByLine = searchMatchesByLine,
                    activeSearchMatch = activeSearchMatch,
                    semanticColors = semanticColors,
                    strings = strings,
                    isFoldingEnabled = isFoldingEnabled,
                    isWordWrapEnabled = isWordWrapEnabled,
                    gutterWidthDp = gutterWidth,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    lazyListState = lazyListState,
                    horizontalScrollState = horizontalScrollState,
                    caret = caret,
                    onCaretChange = onCaretChange,
                    selection = selection,
                    shouldRequestEditorFocus = shouldRequestEditorFocus,
                    onToggleFold = onToggleFold,
                    onLineChanged = onLineChanged,
                    onLineSplit = onLineSplit,
                    onLineMerge = onLineMerge,
                    onMultiLinePaste = onMultiLinePaste,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onTextLayout = { lineIndex, layout ->
                        if (layout == null) lineTextLayouts.remove(lineIndex) else lineTextLayouts[lineIndex] = layout
                    }
                )

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(lazyListState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().pointerHoverIcon(PointerIcon.Default),
                    style = scrollbarStyle
                )
                if (!isWordWrapEnabled) {
                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(horizontalScrollState),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(CodeEditorTokens.ScrollbarThickness)
                            .pointerHoverIcon(PointerIcon.Default),
                        style = scrollbarStyle
                    )
                }
            }
        }
    }
}
