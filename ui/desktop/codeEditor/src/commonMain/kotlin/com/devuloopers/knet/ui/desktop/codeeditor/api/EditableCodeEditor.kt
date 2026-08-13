package com.devuloopers.knet.ui.desktop.codeeditor.api

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.AutoIndentEngine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentBuffer
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldManager
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.PasteEngine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.EditKind
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.UndoRedoStack
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.component.EditorHeaderToolbar
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBodyMode
import com.devuloopers.knet.ui.desktop.codeeditor.component.rememberClipboardCopyAction
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorStyle

import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controller composable for rendering an editable code editor viewport.
 *
 * Encapsulates line-indexed document buffer mutations, undo/redo history tracking, fold regions,
 * and keyboard input callbacks.
 */
@Composable
internal fun EditableCodeEditor(
    code: String,
    mode: EditorMode.Editable,
    style: CodeEditorStyle,
    languageHint: String?,
    isFoldingEnabled: Boolean,
    showLineCountHeader: Boolean,
    showFoldActionsHeader: Boolean,
    isWordWrapEnabled: Boolean,
    modifier: Modifier
) {
    val documentBuffer = remember { DocumentBuffer(code.lines()) }
    val undoStack = remember { UndoRedoStack().apply { init(code) } }

    var rawLines by remember { mutableStateOf(documentBuffer.getLines()) }
    var foldRegions by remember { mutableStateOf<List<FoldRegion>>(emptyList()) }
    var collapsedFoldStartLines by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var caretState by remember { mutableStateOf(EditorCaretState(0, 0)) }
    var activeSelection by remember { mutableStateOf<EditorSelection?>(null) }

    LaunchedEffect(code) {
        val currentText = documentBuffer.toFullText()
        if (currentText != code) {
            documentBuffer.replaceAll(code.lines())
            undoStack.init(code)
            rawLines = documentBuffer.getLines()
            collapsedFoldStartLines = emptySet()
            activeSelection = null
        }
    }

    LaunchedEffect(rawLines) {
        if (isFoldingEnabled) {
            foldRegions = withContext(Dispatchers.Default) {
                FoldManager.calculateFolds(rawLines, respectLineThreshold = false)
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val copyAction = rememberClipboardCopyAction()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(style.backgroundColor, RoundedCornerShape(6.dp))
            .border(1.dp, EditorColors.BorderDark.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(CodeEditorTokens.ContainerPadding)
    ) {
        EditorHeaderToolbar(
            totalLines = rawLines.size,
            showLineCountHeader = showLineCountHeader,
            showFoldActionsHeader = showFoldActionsHeader,
            hasFoldRegions = foldRegions.isNotEmpty(),
            isHighPerformanceMode = false,
            isTruncated = false,
            displayedLines = rawLines.size,
            onCopyAll = {
                coroutineScope.launch(Dispatchers.IO) {
                    copyAction(documentBuffer.toFullText())
                }
            },
            onPrettify = mode.onPrettify,
            onExpandAll = {
                collapsedFoldStartLines = emptySet()
                mode.onCodeChange(documentBuffer.toFullText())
            },
            onCollapseAll = {
                collapsedFoldStartLines = foldRegions.map { it.startLine }.toSet()
                mode.onCodeChange(documentBuffer.toFullText())
            }
        )

        if (rawLines.size == 1 && rawLines[0].isEmpty() && mode.placeholder.isNotEmpty()) {
            Text(
                text = mode.placeholder,
                color = EditorColors.TextSecondary.copy(alpha = 0.4f),
                fontFamily = FontFamily.Monospace,
                style = CodeEditorTokens.editorTextStyle(
                    fontSize = style.fontSize,
                    lineHeight = style.lineHeight
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyCodeBody(
            rawLines = rawLines,
            mode = LazyCodeBodyMode.Editable,
            foldRegions = foldRegions,
            collapsedFoldStartLines = collapsedFoldStartLines,
            onToggleFold = { lineIndex ->
                collapsedFoldStartLines = if (lineIndex in collapsedFoldStartLines) {
                    collapsedFoldStartLines - lineIndex
                } else {
                    collapsedFoldStartLines + lineIndex
                }
            },
            isFoldingEnabled = isFoldingEnabled,
            isWordWrapEnabled = isWordWrapEnabled,
            languageHint = languageHint,
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            caretState = caretState,
            onCaretStateChange = { newCaret ->
                caretState = newCaret
                activeSelection = null
                undoStack.updatePendingBeforeState(newCaret, selection = null)
            },
            selection = activeSelection,
            onSelectionChange = { newSelection ->
                activeSelection = newSelection
                if (newSelection != null) {
                    undoStack.updatePendingBeforeState(caretState, selection = newSelection)
                }
            },
            onUndo = {
                val result = undoStack.undo()
                if (result != null) {
                    documentBuffer.replaceAll(result.text.lines())
                    rawLines = documentBuffer.getLines()
                    caretState = result.caretState
                    activeSelection = result.selection
                    mode.onCodeChange(result.text)
                }
            },
            onRedo = {
                val result = undoStack.redo()
                if (result != null) {
                    documentBuffer.replaceAll(result.text.lines())
                    rawLines = documentBuffer.getLines()
                    caretState = result.caretState
                    activeSelection = result.selection
                    mode.onCodeChange(result.text)
                }
            },
            onDocumentLinesChanged = { updatedLines ->
                // beforeCaret is automatically tracked by undoStack.updatePendingBeforeCaret via onCaretStateChange
                documentBuffer.replaceAll(updatedLines)
                undoStack.push(documentBuffer.toFullText(), afterCaret = caretState, editKind = EditKind.Structural)
                rawLines = documentBuffer.getLines()
                mode.onCodeChange(documentBuffer.toFullText())
            },
            onMultiLinePaste = { lineIndex, caretCol, pastedText ->
                val newCaretState = PasteEngine.applyPaste(
                    buffer = documentBuffer,
                    lineIndex = lineIndex,
                    caretCol = caretCol,
                    pastedText = pastedText
                )
                undoStack.push(documentBuffer.toFullText(), afterCaret = newCaretState, editKind = EditKind.Structural)
                rawLines = documentBuffer.getLines()
                caretState = newCaretState
                undoStack.updatePendingBeforeState(newCaretState)
                mode.onCodeChange(documentBuffer.toFullText())
            },
            onLineChanged = { lineIndex, newText ->
                documentBuffer.setLine(lineIndex, newText)
                // afterCaret matches current caretState: onFocused already updated it via onCaretStateChange
                undoStack.push(documentBuffer.toFullText(), afterCaret = caretState)
                rawLines = documentBuffer.getLines()
                mode.onCodeChange(documentBuffer.toFullText())
            },
            onLineSplit = { lineIndex, colIndex ->
                val indent = AutoIndentEngine.computeIndentForSplit(
                    lineText = rawLines.getOrElse(lineIndex) { "" },
                    colIndex = colIndex
                )
                documentBuffer.splitLine(lineIndex, colIndex, trailingIndent = indent)
                val newCaretState = EditorCaretState(lineIndex + 1, indent.length)
                undoStack.push(documentBuffer.toFullText(), afterCaret = newCaretState, editKind = EditKind.Structural)
                rawLines = documentBuffer.getLines()
                caretState = newCaretState
                undoStack.updatePendingBeforeState(newCaretState)
                mode.onCodeChange(documentBuffer.toFullText())
            },
            onLineMerge = { lineIndex ->
                if (lineIndex > 0) {
                    val prevLineLen = rawLines[lineIndex - 1].length
                    documentBuffer.mergeLines(lineIndex)
                    val newCaretState = EditorCaretState(lineIndex - 1, prevLineLen)
                    undoStack.push(documentBuffer.toFullText(), afterCaret = newCaretState, editKind = EditKind.Structural)
                    rawLines = documentBuffer.getLines()
                    caretState = newCaretState
                    undoStack.updatePendingBeforeState(newCaretState)
                    mode.onCodeChange(documentBuffer.toFullText())
                }
            },


            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}
