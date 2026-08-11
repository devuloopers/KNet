package com.devuloopers.knet.ui.desktop.codeeditor.api

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.*
import com.devuloopers.knet.ui.desktop.codeeditor.component.*
import com.devuloopers.knet.ui.desktop.codeeditor.component.ContextMenuItem
import com.devuloopers.knet.ui.desktop.codeeditor.model.PreparedDocument
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.FsmTokenMakerVisualTransformation
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorStyle
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.devuloopers.knet.ui.core.components.placeholder.KNetBodyLoadingPlaceholder
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.TokenMaker
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.CodeHighlighterRegistry

/** Maximum displayed line preview threshold (10,000 lines) for zero-latency 100k+ line responses. */
const val LARGE_PAYLOAD_LINE_THRESHOLD = 10000

/** Byte size threshold (2 MB) above which fast-path rendering is automatically enabled. */
const val LARGE_PAYLOAD_BYTE_THRESHOLD = 2 * 1024 * 1024

/**
 * Result data class holding coroutine-processed background payload state.
 */
data class ProcessedPayloadState(
    val displayedText: String,
    val totalLineCount: Int,
    val displayedLineCount: Int,
    val isTruncated: Boolean
)

/**
 * Unified Code Editor and Viewer Component supporting both ReadOnly and Editable modes.
 *
 * Employs BasicTextField natively across both modes for 100% native multi-line text selection
 * with inline-bracket code folding, LRU token caching, Kotlin Coroutine background offloading,
 * and high-performance safety safeguards for 100,000+ line (1 Lakh+ line) responses.
 */
@Composable
fun KNetCodeEditor(
    document: PreparedDocument,
    mode: EditorMode = EditorMode.ReadOnly,
    modifier: Modifier = Modifier,
    style: CodeEditorStyle = CodeEditorStyle(),
    searchQuery: String = "",
    isFoldingEnabled: Boolean = true,
    showLineCountHeader: Boolean = true,
    showFoldActionsHeader: Boolean = true,
    isWordWrapEnabled: Boolean = true
) {
    KNetCodeEditor(
        code = document.formattedText.ifBlank { document.rawText },
        document = document,
        mode = mode,
        modifier = modifier,
        style = style,
        languageHint = document.statistics.language,
        searchQuery = searchQuery,
        isFoldingEnabled = isFoldingEnabled,
        showLineCountHeader = showLineCountHeader,
        showFoldActionsHeader = showFoldActionsHeader,
        isWordWrapEnabled = isWordWrapEnabled
    )
}

@Composable
fun KNetCodeEditor(
    code: String,
    mode: EditorMode,
    modifier: Modifier = Modifier,
    style: CodeEditorStyle = CodeEditorStyle(),
    document: PreparedDocument? = null,
    languageHint: String? = null,
    bodyFormat: BodyFormat? = null,
    searchQuery: String = "",
    isFoldingEnabled: Boolean = true,
    showLineCountHeader: Boolean = true,
    showFoldActionsHeader: Boolean = true,
    isWordWrapEnabled: Boolean = true
) {
    var collapsedFolds by remember { mutableStateOf(mapOf<Int, CollapsedFoldState>()) }

    fun getFullText(displayedText: String): String {
        if (collapsedFolds.isEmpty()) return displayedText
        val result = displayedText.lines().toMutableList()
        var cumulativeOffset = 0
        collapsedFolds.entries.sortedBy { it.key }.forEach { (startLine, state) ->
            val targetIndex = startLine + cumulativeOffset
            if (targetIndex in result.indices) {
                result[targetIndex] = state.originalHeader
                result.addAll(targetIndex + 1, state.hiddenLines)
                cumulativeOffset += state.hiddenLines.size
            }
        }
        return result.joinToString("\n")
    }

    when (mode) {
        is EditorMode.Editable -> {
            EditableCodeEditor(
                code = code,
                mode = mode,
                style = style,
                collapsedFolds = collapsedFolds,
                onCollapsedFoldsChange = { collapsedFolds = it },
                getFullText = ::getFullText,
                isFoldingEnabled = isFoldingEnabled,
                showLineCountHeader = showLineCountHeader,
                showFoldActionsHeader = showFoldActionsHeader,
                isWordWrapEnabled = isWordWrapEnabled,
                modifier = modifier
            )
        }

        is EditorMode.ReadOnly -> {
            ReadOnlyCodeViewer(
                code = code,
                document = document,
                style = style,
                languageHint = languageHint,
                bodyFormat = bodyFormat,
                searchQuery = searchQuery,
                isFoldingEnabled = isFoldingEnabled,
                showLineCountHeader = showLineCountHeader,
                showFoldActionsHeader = showFoldActionsHeader,
                isWordWrapEnabled = isWordWrapEnabled,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditableCodeEditor(
    code: String,
    mode: EditorMode.Editable,
    style: CodeEditorStyle,
    collapsedFolds: Map<Int, CollapsedFoldState>,
    onCollapsedFoldsChange: (Map<Int, CollapsedFoldState>) -> Unit,
    getFullText: (String) -> String,
    isFoldingEnabled: Boolean,
    showLineCountHeader: Boolean,
    showFoldActionsHeader: Boolean,
    isWordWrapEnabled: Boolean,
    modifier: Modifier
) {
    val undoStack = remember { UndoRedoStack().apply { init(code) } }
    val density = LocalDensity.current

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = code,
                selection = TextRange(code.length)
            )
        )
    }

    var lineTopOffsetsDp by remember { mutableStateOf<List<Dp>>(emptyList()) }

    LaunchedEffect(code) {
        val currentFullText = getFullText(textFieldValue.text)
        if (currentFullText != code) {
            textFieldValue = TextFieldValue(text = code, selection = TextRange(code.length))
            onCollapsedFoldsChange(emptyMap())
            undoStack.init(code)
        }
    }

    val isHighPerformanceMode = remember(code.length) {
        code.length > LARGE_PAYLOAD_BYTE_THRESHOLD
    }

    val fullText = remember(textFieldValue.text, collapsedFolds) { getFullText(textFieldValue.text) }

    val lineCount = remember(textFieldValue.text) {
        textFieldValue.text.count { it == '\n' } + 1
    }

    val totalLineCount = remember(fullText) {
        fullText.count { it == '\n' } + 1
    }

    val layoutMap = remember(totalLineCount, collapsedFolds) {
        DocumentLayoutMap.build(totalLineCount, collapsedFolds)
    }

    val foldRegions = remember(fullText, isHighPerformanceMode, isFoldingEnabled) {
        if (isFoldingEnabled && !isHighPerformanceMode && totalLineCount <= LARGE_PAYLOAD_LINE_THRESHOLD) {
            FoldManager.calculateFolds(fullText.lines())
        } else {
            emptyList()
        }
    }
    val foldStartLines = remember(foldRegions, layoutMap) {
        foldRegions.mapNotNull { region ->
            layoutMap.toDisplayedLine(region.startLine)?.let { dispIdx -> dispIdx to region }
        }.toMap()
    }

    val caretOffset = textFieldValue.selection.start
    val activeLineIndex = remember(textFieldValue.text, caretOffset) {
        var line = 0
        val max = caretOffset.coerceAtMost(textFieldValue.text.length)
        for (i in 0 until max) {
            if (textFieldValue.text[i] == '\n') line++
        }
        line
    }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val tokenTransformation = remember { FsmTokenMakerVisualTransformation() }

    val autoScrollController = rememberAutoScrollController()
    var containerHeightPx by remember { mutableStateOf(0f) }
    val thresholdPx = with(density) { autoScrollController.activationZoneDp.toPx() }

    fun toggleFold(lineIndex: Int) {
        val updatedText = performFoldToggle(
            lineIndex = lineIndex,
            displayedText = textFieldValue.text,
            collapsedFolds = collapsedFolds,
            foldStartLines = foldStartLines,
            onCollapsedFoldsChange = onCollapsedFoldsChange
        )
        val safeSelection = TextRange(textFieldValue.selection.start.coerceAtMost(updatedText.length))
        textFieldValue = TextFieldValue(updatedText, selection = safeSelection)
        mode.onCodeChange(getFullText(updatedText))
    }

    val copyAction = rememberClipboardCopyAction()

    val customTextContextMenu = remember(textFieldValue) {
        object : TextContextMenu {
            @Composable
            override fun Area(
                textManager: TextContextMenu.TextManager,
                state: ContextMenuState,
                content: @Composable () -> Unit
            ) {
                val selectedText = textManager.selectedText.text.ifEmpty {
                    if (!textFieldValue.selection.collapsed) {
                        val min = minOf(textFieldValue.selection.start, textFieldValue.selection.end)
                        val max = maxOf(textFieldValue.selection.start, textFieldValue.selection.end)
                        if (min in 0..textFieldValue.text.length && max in 0..textFieldValue.text.length) {
                            textFieldValue.text.substring(min, max)
                        } else ""
                    } else ""
                }
                val hasSelection = selectedText.isNotBlank()
                val menuItems = mutableListOf<ContextMenuItem>()

                if (hasSelection) {
                    menuItems.add(
                        ContextMenuItem(
                            label = "Copy Selected Text",
                            shortcut = "Ctrl+C",
                            onClick = {
                                copyAction(selectedText)
                            }
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

    CompositionLocalProvider(LocalTextContextMenu provides customTextContextMenu) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(style.backgroundColor, RoundedCornerShape(6.dp))
                .border(1.dp, EditorColors.BorderDark.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(CodeEditorTokens.ContainerPadding)
        ) {
        EditorHeaderToolbar(
            totalLines = totalLineCount,
            showLineCountHeader = showLineCountHeader,
            showFoldActionsHeader = showFoldActionsHeader,
            hasFoldRegions = foldRegions.isNotEmpty(),
            isHighPerformanceMode = isHighPerformanceMode,
            onExpandAll = {
                val full = getFullText(textFieldValue.text)
                onCollapsedFoldsChange(emptyMap())
                textFieldValue = TextFieldValue(full, selection = TextRange(full.length))
                mode.onCodeChange(full)
            },
            onCollapseAll = {
                val full = getFullText(textFieldValue.text)
                val (collapsedText, newFolds) = collapseAllFolds(full)
                onCollapsedFoldsChange(newFolds)
                textFieldValue = TextFieldValue(collapsedText, selection = TextRange(collapsedText.length))
                mode.onCodeChange(full)
            }
        )

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

        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerHeightPx = it.height.toFloat() }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val isPressed = event.buttons.isPrimaryPressed
                                if (isPressed && event.changes.isNotEmpty()) {
                                    val pointerY = event.changes.first().position.y
                                    autoScrollController.handleDragPointer(
                                        mouseY = pointerY,
                                        containerHeightPx = containerHeightPx,
                                        thresholdPx = thresholdPx,
                                        scrollState = verticalScrollState
                                    )
                                } else {
                                    autoScrollController.stop()
                                }
                            }
                        }
                    }
                    .verticalScroll(verticalScrollState)
            ) {
                EditorGutter(
                    lineCount = lineCount,
                    activeLineIndex = activeLineIndex,
                    lineTopOffsetsDp = lineTopOffsetsDp,
                    collapsedFolds = collapsedFolds,
                    foldStartLines = foldStartLines,
                    isFoldingEnabled = isFoldingEnabled && !isHighPerformanceMode,
                    isIconArrowStyle = false,
                    layoutMap = layoutMap,
                    onToggleFold = ::toggleFold
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (!isWordWrapEnabled) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            val autoIndentedValue = AutoIndentEngine.handleInsertBreak(
                                oldValue = textFieldValue,
                                newValue = newValue
                            )
                            val targetValue = autoIndentedValue ?: newValue
                            textFieldValue = targetValue
                            undoStack.push(targetValue.text)
                            mode.onCodeChange(getFullText(targetValue.text))
                        },
                        onTextLayout = { layoutResult ->
                            val (measuredTops, _) = measureLineLayoutOffsets(layoutResult, density)
                            lineTopOffsetsDp = measuredTops
                        },
                        visualTransformation = if (isHighPerformanceMode) VisualTransformation.None else tokenTransformation,
                        cursorBrush = SolidColor(EditorColors.ActiveBlue),
                        textStyle = CodeEditorTokens.editorTextStyle(
                            fontSize = style.fontSize,
                            lineHeight = style.lineHeight
                        ).copy(
                            color = mode.textColor,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxSize(),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.TopStart) {
                                if (textFieldValue.text.isEmpty() && mode.placeholder.isNotEmpty()) {
                                    Text(
                                        text = mode.placeholder,
                                        color = EditorColors.TextSecondary.copy(alpha = 0.4f),
                                        fontFamily = FontFamily.Monospace,
                                        style = CodeEditorTokens.editorTextStyle(
                                            fontSize = style.fontSize,
                                            lineHeight = style.lineHeight
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(verticalScrollState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                style = scrollbarStyle
            )
        }
    }
}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadOnlyCodeViewer(
    code: String,
    document: PreparedDocument?,
    style: CodeEditorStyle,
    languageHint: String?,
    bodyFormat: BodyFormat?,
    searchQuery: String,
    isFoldingEnabled: Boolean,
    showLineCountHeader: Boolean,
    showFoldActionsHeader: Boolean,
    isWordWrapEnabled: Boolean,
    modifier: Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isSearching = searchQuery.isNotBlank()
    val copyAction = rememberClipboardCopyAction()

    val payloadState by produceState<ProcessedPayloadState?>(
        initialValue = null,
        key1 = code,
        key2 = searchQuery,
        key3 = document
    ) {
        value = if (document != null && !isSearching) {
            ProcessedPayloadState(
                displayedText = document.previewText,
                totalLineCount = document.statistics.totalLines,
                displayedLineCount = minOf(document.statistics.totalLines, document.statistics.previewLineLimit),
                isTruncated = document.statistics.isTruncated
            )
        } else {
            withContext(Dispatchers.Default) {
                val lines = if (!isSearching) {
                    code.lines()
                } else {
                    code.lines().filter { it.contains(searchQuery, ignoreCase = true) }
                }
                val total = lines.size
                val isTruncated = total > LARGE_PAYLOAD_LINE_THRESHOLD
                val previewText = if (isTruncated) {
                    lines.take(LARGE_PAYLOAD_LINE_THRESHOLD).joinToString("\n")
                } else {
                    lines.joinToString("\n")
                }
                ProcessedPayloadState(
                    displayedText = previewText,
                    totalLineCount = total,
                    displayedLineCount = if (isTruncated) LARGE_PAYLOAD_LINE_THRESHOLD else total,
                    isTruncated = isTruncated
                )
            }
        }
    }

    val activePayload = payloadState ?: if (document != null && !isSearching) {
        ProcessedPayloadState(
            displayedText = document.previewText,
            totalLineCount = document.statistics.totalLines,
            displayedLineCount = minOf(document.statistics.totalLines, document.statistics.previewLineLimit),
            isTruncated = document.statistics.isTruncated
        )
    } else {
        ProcessedPayloadState(
            displayedText = code,
            totalLineCount = code.count { it == '\n' } + 1,
            displayedLineCount = code.count { it == '\n' } + 1,
            isTruncated = false
        )
    }

    var rawLinesState by remember { mutableStateOf<List<String>?>(null) }
    var foldRegionsState by remember { mutableStateOf<List<FoldRegion>>(emptyList()) }
    var collapsedFoldStartLines by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(activePayload.displayedText) {
        rawLinesState = null
        collapsedFoldStartLines = emptySet()
        val lines = withContext(Dispatchers.Default) {
            activePayload.displayedText.split("\n")
        }
        rawLinesState = lines
        val folds = document?.folding ?: withContext(Dispatchers.Default) {
            FoldManager.calculateFolds(lines, respectLineThreshold = false)
        }
        foldRegionsState = folds
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EditorColors.BackgroundDark, RoundedCornerShape(4.dp))
            .padding(CodeEditorTokens.ContainerPadding)
    ) {
        EditorHeaderToolbar(
            totalLines = activePayload.totalLineCount,
            showLineCountHeader = showLineCountHeader,
            showFoldActionsHeader = showFoldActionsHeader && !isSearching,
            hasFoldRegions = foldRegionsState.isNotEmpty(),
            isHighPerformanceMode = false,
            isTruncated = activePayload.isTruncated,
            displayedLines = activePayload.displayedLineCount,
            onCopyAll = {
                coroutineScope.launch(Dispatchers.IO) {
                    copyAction(code)
                }
            },
            onExpandAll = {
                collapsedFoldStartLines = emptySet()
            },
            onCollapseAll = {
                collapsedFoldStartLines = foldRegionsState.map { it.startLine }.toSet()
            }
        )

        val currentRawLines = rawLinesState
        if (currentRawLines != null) {
            LazyReadOnlyBody(
                rawLines = currentRawLines,
                foldRegions = foldRegionsState,
                collapsedFoldStartLines = collapsedFoldStartLines,
                onToggleFold = { lineIndex ->
                    collapsedFoldStartLines = if (lineIndex in collapsedFoldStartLines) {
                        collapsedFoldStartLines - lineIndex
                    } else {
                        collapsedFoldStartLines + lineIndex
                    }
                },
                isFoldingEnabled = isFoldingEnabled && !isSearching,
                isWordWrapEnabled = isWordWrapEnabled,
                languageHint = document?.statistics?.language ?: languageHint,
                fontSize = style.fontSize,
                lineHeight = style.lineHeight,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            KNetBodyLoadingPlaceholder(
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}
