package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import com.devuloopers.knet.ui.desktop.codeeditor.component.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection

/**
 * Describes the direction and type of a document edit for coalescing boundary detection.
 *
 * Used by [UndoRedoStack.shouldCoalesce] to determine whether a new edit should be merged
 * into the current undo group or start a fresh transaction. Follows the Strongly-Typed
 * Contracts rule — no raw strings or integer flags.
 */
enum class EditKind {
    /**
     * A character or sequence of characters was inserted at the caret position.
     * Consecutive insertions may be coalesced into one undo entry if no boundary rule fires.
     */
    Insertion,

    /**
     * A character or sequence of characters was deleted (Backspace on a single character).
     * Consecutive deletions may be coalesced into one undo entry if no boundary rule fires.
     */
    Deletion,

    /**
     * A structural mutation that always starts its own undo group:
     * line split (Enter), line merge (Backspace at start), multi-line paste, cut, or
     * bulk selection deletion. These are never coalesced with adjacent typing bursts.
     */
    Structural
}

/**
 * Single edit operation entry in the undo/redo stack.
 *
 * Stores both the text model mutation, the 2D cursor positions, and the active selection state
 * before and after the edit, matching VS Code's `UndoRedoElement` architecture.
 *
 * @property text The document text content AFTER this edit.
 * @property beforeCaret 2D cursor position BEFORE this edit started.
 * @property afterCaret 2D cursor position AFTER this edit completed.
 * @property beforeSelection Active selection range BEFORE this edit started (e.g. selection deleted via Backspace).
 * @property afterSelection Active selection range AFTER this edit completed.
 * @property editKind The kind of edit for coalescing boundary detection.
 * @property timestamp System epoch timestamp when edit occurred.
 */
data class UndoRedoElement(
    val text: String,
    val beforeCaret: EditorCaretState,
    val afterCaret: EditorCaretState,
    val beforeSelection: EditorSelection? = null,
    val afterSelection: EditorSelection? = null,
    val editKind: EditKind,
    val timestamp: Long = currentSystemTimeMillis()
)

internal fun currentSystemTimeMillis(): Long {
    return try {
        System.currentTimeMillis()
    } catch (_: Throwable) {
        0L
    }
}

/**
 * Result object returned by [UndoRedoStack.undo] containing the reverted text, restored cursor position,
 * and restored active text selection range.
 *
 * @property text Document text to restore.
 * @property caretState 2D cursor position to restore (the `beforeCaret` of the undone edit).
 * @property selection Active selection range to restore (the `beforeSelection` of the undone edit).
 */
data class UndoResult(
    val text: String,
    val caretState: EditorCaretState,
    val selection: EditorSelection?
)

/**
 * Result object returned by [UndoRedoStack.redo] containing the redone text, restored cursor position,
 * and restored active text selection range.
 *
 * @property text Document text to re-apply.
 * @property caretState 2D cursor position to restore (the `afterCaret` of the redone edit).
 * @property selection Active selection range to restore (the `afterSelection` of the redone edit).
 */
data class RedoResult(
    val text: String,
    val caretState: EditorCaretState,
    val selection: EditorSelection?
)

/**
 * A compound edit history stack for code editing with modern IDE-standard boundary-aware
 * transaction coalescing and selection restoration.
 *
 * Implements VS Code's `UndoRedoElement` before/after cursor and selection restoration model combined with
 * IntelliJ IDEA's five-rule coalescing boundary detection:
 *
 * 1. **Whitespace boundary**: Inserting Space or Tab finalizes the previous word group.
 * 2. **Delimiter boundary**: Inserting punctuation (`;`, `,`, `(`, `)`, `=`, etc.) finalizes the group.
 * 3. **Edit direction change**: Switching from insertion to deletion or vice versa starts a new group.
 * 4. **Cursor discontinuity**: A non-sequential cursor jump (mouse, arrow to different position) finalizes the group.
 * 5. **Time pause**: A gap of more than [coalesceTimeoutMs] milliseconds between keystrokes finalizes the group.
 *
 * The `pendingBeforeCaret` & `pendingBeforeSelection` design:
 * - [updatePendingBeforeState] is called on every cursor navigation, mouse click, or selection drag, so that
 *   the `beforeCaret` & `beforeSelection` recorded for the NEXT push always reflects the position immediately
 *   before the user began an edit.
 *
 * @param maxStackSize Maximum number of edit entries to retain before evicting the oldest.
 * @param coalesceTimeoutMs Maximum time gap in milliseconds between edits that can still be coalesced.
 */
class UndoRedoStack(
    private val maxStackSize: Int = 100,
    private val coalesceTimeoutMs: Long = 1000L
) {
    private val history = mutableListOf<UndoRedoElement>()
    private var pointer = -1
    private var initialText = ""

    /**
     * The cursor position that will be recorded as `beforeCaret` on the next [push].
     */
    private var pendingBeforeCaret: EditorCaretState = EditorCaretState(0, 0)

    /**
     * The active text selection range that will be recorded as `beforeSelection` on the next [push].
     */
    private var pendingBeforeSelection: EditorSelection? = null

    /**
     * When `true`, the next [push] call must start a fresh undo group regardless of other rules.
     */
    private var pendingGroupBreak: Boolean = false

    /** Whether there are entries available to undo. */
    val canUndo: Boolean
        get() = pointer >= 0

    /** Whether there are entries available to redo. */
    val canRedo: Boolean
        get() = pointer < history.lastIndex

    /**
     * Initializes the stack with starting text content and resets all history.
     *
     * @param startingText The initial document content before any edits.
     */
    fun init(startingText: String) {
        history.clear()
        initialText = startingText
        pointer = -1
        pendingBeforeCaret = EditorCaretState(0, 0)
        pendingBeforeSelection = null
        pendingGroupBreak = false
    }

    /**
     * Updates the cursor position and active selection state that will be captured as `beforeCaret`
     * and `beforeSelection` for the next [push].
     *
     * Must be called on every cursor navigation event (arrow keys, mouse click, selection drag)
     * so that the undo stack always knows the state immediately before an edit begins.
     *
     * @param caret The current cursor position after navigation or selection change.
     * @param selection The active text selection range, or `null` if no selection active.
     */
    fun updatePendingBeforeState(caret: EditorCaretState, selection: EditorSelection? = null) {
        pendingBeforeCaret = caret
        pendingBeforeSelection = selection
    }

    /**
     * Pushes a new document text mutation into the undo stack.
     *
     * Uses [pendingBeforeCaret] and [pendingBeforeSelection] automatically — callers only need
     * to supply the post-edit [afterCaret] position and optional [afterSelection].
     *
     * @param newText Document text after mutation.
     * @param afterCaret Cursor position after mutation completed.
     * @param afterSelection Selection range after mutation completed (usually `null`).
     * @param editKind The kind of edit. Defaults to [EditKind.Insertion] for normal typing.
     */
    fun push(
        newText: String,
        afterCaret: EditorCaretState,
        afterSelection: EditorSelection? = null,
        editKind: EditKind = EditKind.Insertion
    ) {
        val currentText = if (pointer >= 0) history[pointer].text else initialText
        if (currentText == newText) {
            return
        }

        val currentTime = currentSystemTimeMillis()

        // Attempt coalescing with the most recent history entry
        if (pointer >= 0 && pointer == history.lastIndex) {
            val last = history[pointer]
            if (shouldCoalesce(last, newText, afterCaret, editKind, currentTime)) {
                // Merge into the existing entry: update text and afterCaret, preserve beforeCaret and beforeSelection
                history[pointer] = last.copy(
                    text = newText,
                    afterCaret = afterCaret,
                    afterSelection = afterSelection,
                    editKind = editKind,
                    timestamp = currentTime
                )
                return
            }
        }

        // Truncate any redo entries ahead of pointer (new branch invalidates the future)
        while (history.size > pointer + 1) {
            history.removeAt(history.lastIndex)
        }

        history.add(
            UndoRedoElement(
                text = newText,
                beforeCaret = pendingBeforeCaret,
                afterCaret = afterCaret,
                beforeSelection = pendingBeforeSelection,
                afterSelection = afterSelection,
                editKind = editKind,
                timestamp = currentTime
            )
        )

        // Reset pending selection after pushing the edit
        pendingBeforeSelection = null

        // Enforce maximum stack depth by evicting the oldest entry
        if (history.size > maxStackSize) {
            history.removeAt(0)
        } else {
            pointer++
        }
    }

    /**
     * Reverts to previous text state and restores the `beforeCaret` cursor position and `beforeSelection`.
     *
     * @return [UndoResult] containing reverted text, restored caret position, and restored selection range,
     * or `null` if cannot undo.
     */
    fun undo(): UndoResult? {
        if (!canUndo) return null
        val element = history[pointer]
        pointer--
        val prevText = if (pointer >= 0) history[pointer].text else initialText
        val restoredCaret = element.beforeCaret
        val restoredSelection = element.beforeSelection
        pendingBeforeCaret = restoredCaret
        pendingBeforeSelection = restoredSelection
        return UndoResult(text = prevText, caretState = restoredCaret, selection = restoredSelection)
    }

    /**
     * Re-applies next text state and restores the `afterCaret` cursor position and `afterSelection`.
     *
     * @return [RedoResult] containing redone text, restored caret position, and restored selection range,
     * or `null` if cannot redo.
     */
    fun redo(): RedoResult? {
        if (!canRedo) return null
        pointer++
        val element = history[pointer]
        val restoredCaret = element.afterCaret
        val restoredSelection = element.afterSelection
        pendingBeforeCaret = restoredCaret
        pendingBeforeSelection = restoredSelection
        return RedoResult(text = element.text, caretState = restoredCaret, selection = restoredSelection)
    }

    /**
     * Determines whether a new edit should be coalesced (merged) into the most recent history entry.
     */
    private fun shouldCoalesce(
        last: UndoRedoElement,
        newText: String,
        afterCaret: EditorCaretState,
        editKind: EditKind,
        currentTime: Long
    ): Boolean {
        // Structural edits always start a new undo group
        if (editKind == EditKind.Structural || last.editKind == EditKind.Structural) return false

        // Edits with an active before-selection should not coalesce with typing
        if (last.beforeSelection != null || pendingBeforeSelection != null) return false

        // pendingGroupBreak: a whitespace or delimiter was appended to the previous group
        if (pendingGroupBreak) {
            pendingGroupBreak = false
            return false
        }

        // Rule 5: Time pause exceeding threshold breaks the group
        if (currentTime - last.timestamp > coalesceTimeoutMs) return false

        // Rule 3: Direction change between insertion and deletion breaks the group
        if (editKind != last.editKind) return false

        // Rule 4: Cursor discontinuity — different line, or column jump greater than 1
        if (afterCaret.lineIndex != last.afterCaret.lineIndex) return false
        if (kotlin.math.abs(afterCaret.colIndex - last.afterCaret.colIndex) > 1) return false

        // Rules 1 & 2: Whitespace and delimiter boundary (insertions only)
        if (editKind == EditKind.Insertion) {
            val insertedChar = newText.getOrNull(afterCaret.colIndex - 1)
            if (insertedChar != null && (insertedChar.isWhitespace() || isDelimiter(insertedChar))) {
                pendingGroupBreak = true
                return true
            }
        }

        return true
    }

    private fun isDelimiter(ch: Char): Boolean =
        ch in ";,.(){}[]=+-*/<>!&|^%~:`\""
}
