package com.devuloopers.knet.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.devuloopers.knet.editor.engine.AutoIndentEngine
import com.devuloopers.knet.editor.engine.CollapsedFoldState
import com.devuloopers.knet.editor.engine.FoldManager
import com.devuloopers.knet.editor.engine.UndoRedoStack
import com.devuloopers.knet.editor.engine.collapseAllFolds
import com.devuloopers.knet.editor.engine.measureLineLayoutOffsets
import com.devuloopers.knet.editor.engine.performFoldToggle
import com.devuloopers.knet.editor.highlighter.FsmTokenMakerVisualTransformation
import com.devuloopers.knet.editor.model.EditorMode
import com.devuloopers.knet.editor.tokens.CodeEditorTokens
import com.devuloopers.knet.editor.tokens.EditorColors
import com.devuloopers.knet.editor.widget.ContextMenuItem
import com.devuloopers.knet.editor.widget.EditorGutter
import com.devuloopers.knet.editor.widget.EditorHeaderToolbar
import com.devuloopers.knet.editor.widget.KNetContextMenuArea
import com.devuloopers.knet.editor.widget.rememberClipboardCopyAction

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
    code: String,
    mode: EditorMode,
    modifier: Modifier = Modifier,
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
                languageHint = languageHint,
                bodyFormat = bodyFormat,
                searchQuery = searchQuery,
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
    }
}

@Composable
private fun EditableCodeEditor(
    code: String,
    mode: EditorMode.Editable,
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
    var lineHeightsDp by remember { mutableStateOf<List<Dp>>(emptyList()) }

    LaunchedEffect(code) {
        val currentFullText = getFullText(textFieldValue.text)
        if (currentFullText != code) {
            textFieldValue = TextFieldValue(text = code, selection = TextRange(code.length))
            onCollapsedFoldsChange(emptyMap())
            undoStack.init(code)
        }
    }

    val lines = textFieldValue.text.lines()
    val lineCount = lines.size.coerceAtLeast(1)

    val fullText = remember(textFieldValue.text, collapsedFolds) { getFullText(textFieldValue.text) }
    val fullLines = remember(fullText) { fullText.lines() }

    val isHighPerformanceMode = remember(fullLines.size, code.length) {
        fullLines.size > LARGE_PAYLOAD_LINE_THRESHOLD || code.length > LARGE_PAYLOAD_BYTE_THRESHOLD
    }

    val foldRegions = remember(fullText, isHighPerformanceMode) {
        if (isFoldingEnabled && !isHighPerformanceMode) FoldManager.calculateFolds(fullLines) else emptyList()
    }
    val foldStartLines = remember(foldRegions) { foldRegions.associateBy { it.startLine } }

    val caretOffset = textFieldValue.selection.start
    val activeLineIndex = remember(textFieldValue.text, caretOffset) {
        var currentOffset = 0
        var foundLine = 0
        for (i in lines.indices) {
            val lineLength = lines[i].length + 1
            if (caretOffset >= currentOffset && caretOffset < currentOffset + lineLength) {
                foundLine = i
                break
            }
            currentOffset += lineLength
        }
        foundLine
    }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val tokenTransformation = remember { FsmTokenMakerVisualTransformation() }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117), RoundedCornerShape(6.dp))
            .border(1.dp, EditorColors.BorderDark.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(CodeEditorTokens.ContainerPadding)
    ) {
        EditorHeaderToolbar(
            totalLines = fullLines.size,
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

        Row(
            modifier = Modifier
                .fillMaxSize()
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
                        val (measuredTops, measuredHeights) = measureLineLayoutOffsets(layoutResult, density)
                        lineTopOffsetsDp = measuredTops
                        lineHeightsDp = measuredHeights
                    },
                    visualTransformation = if (isHighPerformanceMode) VisualTransformation.None else tokenTransformation,
                    cursorBrush = SolidColor(EditorColors.ActiveBlue),
                    textStyle = CodeEditorTokens.editorTextStyle().copy(
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
                                    style = CodeEditorTokens.editorTextStyle()
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadOnlyCodeViewer(
    code: String,
    languageHint: String?,
    bodyFormat: BodyFormat?,
    searchQuery: String,
    collapsedFolds: Map<Int, CollapsedFoldState>,
    onCollapsedFoldsChange: (Map<Int, CollapsedFoldState>) -> Unit,
    getFullText: (String) -> String,
    isFoldingEnabled: Boolean,
    showLineCountHeader: Boolean,
    showFoldActionsHeader: Boolean,
    isWordWrapEnabled: Boolean,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val isSearching = searchQuery.isNotBlank()

    // Offload heavy line splitting, 100k+ truncation, and byte metrics to background CPU thread
    val payloadState by produceState<ProcessedPayloadState?>(initialValue = null, key1 = code, key2 = searchQuery) {
        value = withContext(Dispatchers.Default) {
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

    val activePayload = payloadState ?: ProcessedPayloadState(
        displayedText = code,
        totalLineCount = code.lines().size,
        displayedLineCount = code.lines().size,
        isTruncated = false
    )

    var textFieldValue by remember(activePayload.displayedText) {
        mutableStateOf(
            TextFieldValue(
                text = activePayload.displayedText,
                selection = TextRange.Zero
            )
        )
    }

    var lineTopOffsetsDp by remember { mutableStateOf<List<Dp>>(emptyList()) }
    var lineHeightsDp by remember { mutableStateOf<List<Dp>>(emptyList()) }

    LaunchedEffect(activePayload.displayedText) {
        textFieldValue = TextFieldValue(text = activePayload.displayedText, selection = TextRange.Zero)
        onCollapsedFoldsChange(emptyMap())
    }

    val lines = textFieldValue.text.lines()
    val lineCount = lines.size.coerceAtLeast(1)

    val isHighPerformanceMode = remember(activePayload.totalLineCount, code.length) {
        activePayload.totalLineCount > LARGE_PAYLOAD_LINE_THRESHOLD || code.length > LARGE_PAYLOAD_BYTE_THRESHOLD
    }

    val foldRegions = remember(activePayload.displayedText, isHighPerformanceMode) {
        if (isFoldingEnabled && !isSearching && !isHighPerformanceMode) FoldManager.calculateFolds(lines) else emptyList()
    }
    val foldStartLines = remember(foldRegions) { foldRegions.associateBy { it.startLine } }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val copyAction = rememberClipboardCopyAction()
    val tokenTransformation = remember { FsmTokenMakerVisualTransformation() }

    fun toggleFold(lineIndex: Int) {
        val updatedText = performFoldToggle(
            lineIndex = lineIndex,
            displayedText = textFieldValue.text,
            collapsedFolds = collapsedFolds,
            foldStartLines = foldStartLines,
            onCollapsedFoldsChange = onCollapsedFoldsChange
        )
        textFieldValue = TextFieldValue(updatedText, selection = TextRange.Zero)
    }

    val customTextContextMenu = remember(code, foldRegions, collapsedFolds, textFieldValue) {
        object : TextContextMenu {
            @Composable
            override fun Area(
                textManager: TextContextMenu.TextManager,
                state: ContextMenuState,
                content: @Composable () -> Unit
            ) {
                val selectedText = textManager.selectedText.text
                val hasSelection = selectedText.isNotEmpty()
                val menuItems = mutableListOf<ContextMenuItem>()

                if (hasSelection) {
                    menuItems.add(
                        ContextMenuItem(
                            label = "Copy Selected Text",
                            shortcut = "Ctrl+C",
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    copyAction(selectedText)
                                }
                            }
                        )
                    )
                }

                menuItems.add(
                    ContextMenuItem(
                        label = "Copy Formatted Body",
                        shortcut = if (!hasSelection) "Ctrl+C" else null,
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                copyAction(code)
                            }
                        }
                    )
                )

                if (foldRegions.isNotEmpty()) {
                    menuItems.add(
                        ContextMenuItem(
                            label = "Expand All Blocks",
                            onClick = {
                                onCollapsedFoldsChange(emptyMap())
                                textFieldValue = TextFieldValue(code, selection = TextRange.Zero)
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
            hasFoldRegions = foldRegions.isNotEmpty(),
            isHighPerformanceMode = isHighPerformanceMode,
            isTruncated = activePayload.isTruncated,
            displayedLines = activePayload.displayedLineCount,
            onCopyAll = {
                coroutineScope.launch(Dispatchers.IO) {
                    copyAction(code)
                }
            },
            onExpandAll = {
                onCollapsedFoldsChange(emptyMap())
                textFieldValue = TextFieldValue(code, selection = TextRange.Zero)
            },
            onCollapseAll = {
                val (collapsedText, newFolds) = collapseAllFolds(code)
                onCollapsedFoldsChange(newFolds)
                textFieldValue = TextFieldValue(collapsedText, selection = TextRange.Zero)
            }
        )

        CompositionLocalProvider(LocalTextContextMenu provides customTextContextMenu) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
            ) {
                EditorGutter(
                    lineCount = lineCount,
                    activeLineIndex = -1,
                    lineTopOffsetsDp = lineTopOffsetsDp,
                    collapsedFolds = collapsedFolds,
                    foldStartLines = foldStartLines,
                    isFoldingEnabled = isFoldingEnabled && !isSearching && !isHighPerformanceMode,
                    isIconArrowStyle = true,
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
                            textFieldValue = newValue
                        },
                        onTextLayout = { layoutResult ->
                            val (measuredTops, measuredHeights) = measureLineLayoutOffsets(layoutResult, density)
                            lineTopOffsetsDp = measuredTops
                            lineHeightsDp = measuredHeights
                        },
                        readOnly = true,
                        visualTransformation = if (isHighPerformanceMode) VisualTransformation.None else tokenTransformation,
                        cursorBrush = SolidColor(EditorColors.ActiveBlue),
                        textStyle = CodeEditorTokens.editorTextStyle().copy(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxSize(),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.TopStart) {
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }
    }
}
