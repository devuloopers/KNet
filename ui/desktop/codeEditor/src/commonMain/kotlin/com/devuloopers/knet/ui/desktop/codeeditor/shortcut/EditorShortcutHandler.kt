package com.devuloopers.knet.ui.desktop.codeeditor.shortcut

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentBuffer
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.PasteEngine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.SelectionEngine
import com.devuloopers.knet.ui.desktop.codeeditor.component.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection

/**
 * Single-responsibility handler for evaluating and executing editor keyboard shortcuts.
 *
 * Implements VS Code standard shortcut rules (`editorCommands.ts`, `cursorDeleteOperations.ts`):
 * - [Key.Backspace] / [Key.Delete]: Deletes active selection range or single character.
 * - [Key.C] (Ctrl/Cmd+C): Copies selected text (or entire current line if no selection).
 * - [Key.X] (Ctrl/Cmd+X): Cuts selected text (or entire current line if no selection).
 * - [Key.V] (Ctrl/Cmd+V): Pastes clipboard text (deleting active selection range first).
 * - [Key.A] (Ctrl/Cmd+A): Selects all lines in the document.
 * - [Key.Z] (Ctrl/Cmd+Z): Triggers undo operation.
 * - [Key.Y] (Ctrl/Cmd+Y) / Shift+Ctrl+Z: Triggers redo operation.
 */
object EditorShortcutHandler {

    /**
     * Evaluates Compose [keyEvent] and delegates execution to [processKey].
     */
    fun processKeyEvent(
        keyEvent: KeyEvent,
        rawLines: List<String>,
        selection: EditorSelection?,
        caretState: EditorCaretState?,
        foldRegions: List<FoldRegion> = emptyList(),
        collapsedFoldStartLines: Set<Int> = emptySet(),
        copyAction: (String) -> Unit,
        pasteAction: () -> String?,
        onDocumentLinesChanged: ((List<String>) -> Unit)?,
        onSelectionChange: (EditorSelection?) -> Unit,
        onCaretStateChange: ((EditorCaretState) -> Unit)?,
        onUndo: (() -> Unit)? = null,
        onRedo: (() -> Unit)? = null
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        val isCmdOrCtrl = keyEvent.isMetaPressed || keyEvent.isCtrlPressed
        return processKey(
            key = keyEvent.key,
            isCmdOrCtrl = isCmdOrCtrl,
            isShiftPressed = keyEvent.isShiftPressed,
            rawLines = rawLines,
            selection = selection,
            caretState = caretState,
            foldRegions = foldRegions,
            collapsedFoldStartLines = collapsedFoldStartLines,
            copyAction = copyAction,
            pasteAction = pasteAction,
            onDocumentLinesChanged = onDocumentLinesChanged,
            onSelectionChange = onSelectionChange,
            onCaretStateChange = onCaretStateChange,
            onUndo = onUndo,
            onRedo = onRedo
        )
    }

    /**
     * Evaluates key parameters and executes document mutation or selection action.
     */
    fun processKey(
        key: Key,
        isCmdOrCtrl: Boolean = false,
        isShiftPressed: Boolean = false,
        rawLines: List<String>,
        selection: EditorSelection?,
        caretState: EditorCaretState?,
        foldRegions: List<FoldRegion> = emptyList(),
        collapsedFoldStartLines: Set<Int> = emptySet(),
        copyAction: (String) -> Unit,
        pasteAction: () -> String?,
        onDocumentLinesChanged: ((List<String>) -> Unit)?,
        onSelectionChange: (EditorSelection?) -> Unit,
        onCaretStateChange: ((EditorCaretState) -> Unit)?,
        onUndo: (() -> Unit)? = null,
        onRedo: (() -> Unit)? = null
    ): Boolean {
        if (isCmdOrCtrl) {
            when (key) {
                Key.A -> {
                    // Ctrl+A / Cmd+A: Select All
                    if (rawLines.isNotEmpty()) {
                        val lastIndex = rawLines.lastIndex
                        val lastCol = rawLines[lastIndex].length
                        onSelectionChange(
                            EditorSelection(
                                startLine = 0,
                                startCol = 0,
                                endLine = lastIndex,
                                endCol = lastCol
                            )
                        )
                        return true
                    }
                }
                Key.C -> {
                    // Ctrl+C / Cmd+C: Copy selection (fold-aware) or full current line
                    if (selection != null && !selection.isEmpty) {
                        val textToCopy = SelectionEngine.extractSelectedText(
                            buffer = DocumentBuffer(rawLines),
                            selection = selection,
                            foldRegions = foldRegions,
                            collapsedFoldStartLines = collapsedFoldStartLines
                        )
                        if (textToCopy.isNotEmpty()) {
                            copyAction(textToCopy)
                        }
                    } else if (caretState != null && caretState.lineIndex in rawLines.indices) {
                        // Copy entire current line (VS Code behavior)
                        val currentLineText = rawLines[caretState.lineIndex] + "\n"
                        copyAction(currentLineText)
                    }
                    return true
                }
                Key.X -> {
                    // Ctrl+X / Cmd+X: Cut selection (fold-aware) or full current line
                    val buffer = DocumentBuffer(rawLines)
                    if (selection != null && !selection.isEmpty) {
                        val textToCut = SelectionEngine.extractSelectedText(
                            buffer = buffer,
                            selection = selection,
                            foldRegions = foldRegions,
                            collapsedFoldStartLines = collapsedFoldStartLines
                        )
                        if (textToCut.isNotEmpty()) {
                            copyAction(textToCut)
                        }
                        val newCaret = SelectionEngine.deleteSelectedText(
                            buffer = buffer,
                            selection = selection,
                            foldRegions = foldRegions,
                            collapsedFoldStartLines = collapsedFoldStartLines
                        )
                        onDocumentLinesChanged?.invoke(buffer.getLines())
                        onSelectionChange(null)
                        onCaretStateChange?.invoke(newCaret)
                    } else if (caretState != null && caretState.lineIndex in rawLines.indices) {
                        // Cut entire current line (VS Code behavior)
                        val lineIndex = caretState.lineIndex
                        val lineText = rawLines[lineIndex] + "\n"
                        copyAction(lineText)
                        val updatedLines = rawLines.toMutableList()
                        updatedLines.removeAt(lineIndex)
                        val safeLines = if (updatedLines.isEmpty()) listOf("") else updatedLines
                        val safeLineIndex = lineIndex.coerceIn(0, safeLines.lastIndex)
                        buffer.replaceAll(safeLines)
                        onDocumentLinesChanged?.invoke(safeLines)
                        onCaretStateChange?.invoke(EditorCaretState(safeLineIndex, 0))
                    }
                    return true
                }
                Key.V -> {
                    // Ctrl+V / Cmd+V: Paste clipboard text (deleting active selection first)
                    val clipboardText = pasteAction()
                    if (!clipboardText.isNullOrEmpty()) {
                        val buffer = DocumentBuffer(rawLines)
                        var activeCaret = caretState ?: EditorCaretState(0, 0)
                        if (selection != null && !selection.isEmpty) {
                            activeCaret = SelectionEngine.deleteSelectedText(
                                buffer = buffer,
                                selection = selection,
                                foldRegions = foldRegions,
                                collapsedFoldStartLines = collapsedFoldStartLines
                            )
                            onSelectionChange(null)
                        }
                        val newCaret = PasteEngine.applyPaste(
                            buffer = buffer,
                            lineIndex = activeCaret.lineIndex,
                            caretCol = activeCaret.colIndex,
                            pastedText = clipboardText
                        )
                        onDocumentLinesChanged?.invoke(buffer.getLines())
                        onCaretStateChange?.invoke(newCaret)
                    }
                    return true
                }
                Key.Z -> {
                    // Ctrl+Z / Cmd+Z: Undo (or Redo if Shift is held)
                    if (isShiftPressed) {
                        onRedo?.invoke()
                    } else {
                        onUndo?.invoke()
                    }
                    return true
                }
                Key.Y -> {
                    // Ctrl+Y: Redo
                    onRedo?.invoke()
                    return true
                }
            }
        }

        // Handle Backspace or Delete with active selection
        if ((key == Key.Backspace || key == Key.Delete) && selection != null && !selection.isEmpty) {
            val buffer = DocumentBuffer(rawLines)
            val newCaret = SelectionEngine.deleteSelectedText(
                buffer = buffer,
                selection = selection,
                foldRegions = foldRegions,
                collapsedFoldStartLines = collapsedFoldStartLines
            )
            onDocumentLinesChanged?.invoke(buffer.getLines())
            onSelectionChange(null)
            onCaretStateChange?.invoke(newCaret)
            return true
        }

        return false
    }
}
