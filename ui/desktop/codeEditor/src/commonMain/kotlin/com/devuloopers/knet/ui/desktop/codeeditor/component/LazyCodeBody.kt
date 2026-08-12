package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LazyLine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LazyLineVisibilityEngine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LineFoldState
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.PointerHitTestEngine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.rememberAutoScrollController
import com.devuloopers.knet.ui.desktop.codeeditor.component.viewport.LazyCodeBodyContent
import com.devuloopers.knet.ui.desktop.codeeditor.gesture.rememberSelectionGestureHandler
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
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
    val customTextContextMenu = remember(copyAction) {
        object : TextContextMenu {
            @Composable
            override fun Area(
                textManager: TextContextMenu.TextManager,
                state: androidx.compose.foundation.ContextMenuState,
                content: @Composable () -> Unit
            ) {
                val selectedText = textManager.selectedText.text
                val menuItems = mutableListOf<ContextMenuItem>()

                if (selectedText.isNotBlank()) {
                    menuItems.add(
                        ContextMenuItem(
                            label = "Copy Selected Text",
                            shortcut = "Ctrl+C",
                            onClick = { copyAction(selectedText) }
                        )
                    )
                }

                KNetContextMenuArea(
                    items = menuItems,
                    modifier = Modifier.fillMaxSize(),
                    content = content
                )
            }
        }
    }

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

    var internalSelectionState by remember(rawLines) { mutableStateOf<EditorSelection?>(null) }
    val effectiveSelection = selection ?: internalSelectionState
    val currentSelectionState by rememberUpdatedState(effectiveSelection)
    val currentCaretState by rememberUpdatedState(caretState)
    val lineTextLayoutMap = remember { mutableMapOf<Int, TextLayoutResult>() }

    val updateSelection: (EditorSelection?) -> Unit = { newSel ->
        internalSelectionState = newSel
        onSelectionChange?.invoke(newSel)
    }

    CompositionLocalProvider(LocalTextContextMenu provides customTextContextMenu) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(EditorColors.BackgroundDark)
                .onSizeChanged { size ->
                    containerWidthPx = size.width.toFloat()
                    containerHeightPx = size.height.toFloat()
                }
                .pointerInput(rawLines, mode, containerHeightPx, containerWidthPx, gutterWidthPx) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            try {
                                val activeWindow = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                                if (activeWindow != null && activeWindow.cursor.type != java.awt.Cursor.TEXT_CURSOR) {
                                    activeWindow.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.TEXT_CURSOR)
                                }
                            } catch (_: Throwable) {}

                            val isPressed = event.buttons.isPrimaryPressed
                            val isShiftPressed = event.keyboardModifiers.isShiftPressed

                            if (isPressed && event.changes.isNotEmpty()) {
                                val pos = event.changes.first().position

                                autoScrollController.handleDragPointerLazy(
                                    mouseY = pos.y,
                                    containerHeightPx = containerHeightPx,
                                    thresholdPx = autoScrollThresholdPx,
                                    lazyListState = lazyListState
                                )

                                val (lineIndex, colIndex) = PointerHitTestEngine.calculatePointerLineAndCol(
                                    pos = pos,
                                    lazyListState = lazyListState,
                                    rawLines = rawLines,
                                    lineHeightPx = lineHeightPx,
                                    charWidthPx = charWidthPx,
                                    gutterWidthPx = gutterWidthPx,
                                    containerWidthPx = containerWidthPx,
                                    lineTextLayoutMap = lineTextLayoutMap
                                )

                                selectionGestureHandler.processPointerEvent(
                                    targetLineIndex = lineIndex,
                                    targetColIndex = colIndex,
                                    lineText = rawLines.getOrElse(lineIndex) { "" },
                                    isShiftPressed = isShiftPressed,
                                    currentSelection = currentSelectionState,
                                    caretState = currentCaretState,
                                    onSelectionChange = updateSelection
                                )
                            } else {
                                selectionGestureHandler.processPointerRelease(
                                    isShiftPressed = isShiftPressed,
                                    onSelectionChange = updateSelection
                                )
                                autoScrollController.stop()
                            }
                        }
                    }
                }
                .pointerHoverIcon(PointerIcon.Text)
                .onPreviewKeyEvent { keyEvent ->
                    EditorShortcutHandler.processKeyEvent(
                        keyEvent = keyEvent,
                        rawLines = rawLines,
                        selection = effectiveSelection,
                        caretState = caretState,
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
                onSelectionChange = updateSelection,
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
                    .fillMaxHeight(),
                style = scrollbarStyle
            )
        }
    }
}
