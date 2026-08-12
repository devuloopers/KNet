package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import com.devuloopers.knet.ui.desktop.codeeditor.component.EditorCaretState

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
 * Stores both the text model mutation and the 2D cursor positions before and after the edit,
 * matching VS Code's `UndoRedoElement` and `SingleEditOperation` architecture.
 *
 * @property text The document text content AFTER this edit.
 * @property beforeCaret 2D cursor position BEFORE this edit started.
 * @property afterCaret 2D cursor position AFTER this edit completed.
 * @property editKind The kind of edit for coalescing boundary detection.
 * @property timestamp System epoch timestamp when edit occurred.
 */
data class UndoRedoElement(
    val text: String,
    val beforeCaret: EditorCaretState,
    val afterCaret: EditorCaretState,
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
 * Result object returned by [UndoRedoStack.undo] containing the reverted text and restored cursor position.
 *
 * @property text Document text to restore.
 * @property caretState 2D cursor position to restore (the `beforeCaret` of the undone edit).
 */
data class UndoResult(
    val text: String,
    val caretState: EditorCaretState
)

/**
 * Result object returned by [UndoRedoStack.redo] containing the redone text and restored cursor position.
 *
 * @property text Document text to re-apply.
 * @property caretState 2D cursor position to restore (the `afterCaret` of the redone edit).
 */
data class RedoResult(
    val text: String,
    val caretState: EditorCaretState
)

/**
 * A compound edit history stack for code editing with modern IDE-standard boundary-aware
 * transaction coalescing.
 *
 * Implements VS Code's `UndoRedoElement` before/after cursor restoration model combined with
 * IntelliJ IDEA's five-rule coalescing boundary detection:
 *
 * 1. **Whitespace boundary**: Inserting Space or Tab finalizes the previous word group.
 * 2. **Delimiter boundary**: Inserting punctuation (`;`, `,`, `(`, `)`, `=`, etc.) finalizes the group.
 * 3. **Edit direction change**: Switching from insertion to deletion or vice versa starts a new group.
 * 4. **Cursor discontinuity**: A non-sequential cursor jump (mouse, arrow to different position) finalizes the group.
 * 5. **Time pause**: A gap of more than [coalesceTimeoutMs] milliseconds between keystrokes finalizes the group.
 *
 * The `pendingBeforeCaret` design (matching VS Code's `cursorUndo.ts`):
 * - [updatePendingBeforeCaret] is called on every cursor navigation or mouse click, so that the
 *   `beforeCaret` recorded for the NEXT push always reflects the position immediately before the
 *   user began an edit — not the position after [onValueChange] has already updated it.
 * - [push] uses [pendingBeforeCaret] as `beforeCaret` automatically; callers only supply `afterCaret`.
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
     * Updated via [updatePendingBeforeCaret] on every navigation event (arrow keys, mouse click).
     */
    private var pendingBeforeCaret: EditorCaretState = EditorCaretState(0, 0)

    /**
     * When `true`, the next [push] call must start a fresh undo group regardless of other rules.
     *
     * This is set to `true` after a whitespace or delimiter character is inserted. Those characters
     * are appended to the CURRENT group (e.g. `"hello "` stays in one entry), but the NEXT
     * non-delimiter character (`"w"` in `"world"`) must start a new group.
     *
     * This matches VS Code and IntelliJ's word-level undo semantics precisely.
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
        pendingGroupBreak = false
    }

    /**
     * Updates the cursor position that will be captured as `beforeCaret` for the next [push].
     *
     * Must be called on every cursor navigation event (arrow keys, mouse click, undo/redo restore)
     * so that the undo stack always knows the cursor position immediately before an edit begins.
     * This mirrors VS Code's approach of capturing a cursor snapshot on every `onCursorPositionChanged` event.
     *
     * @param caret The current cursor position after navigation.
     */
    fun updatePendingBeforeCaret(caret: EditorCaretState) {
        pendingBeforeCaret = caret
    }

    /**
     * Pushes a new document text mutation into the undo stack.
     *
     * Uses [pendingBeforeCaret] as `beforeCaret` automatically — callers only need to supply
     * the post-edit [afterCaret] position. This ensures `beforeCaret` is always the position
     * the user was at BEFORE they started typing.
     *
     * Coalescing is governed by [shouldCoalesce], which implements all 5 modern IDE boundary rules.
     * Structural edits ([EditKind.Structural]) always start their own undo group.
     *
     * @param newText Document text after mutation.
     * @param afterCaret Cursor position after mutation completed.
     * @param editKind The kind of edit. Defaults to [EditKind.Insertion] for normal typing.
     */
    fun push(
        newText: String,
        afterCaret: EditorCaretState,
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
                // Merge into the existing entry: update text and afterCaret, preserve beforeCaret
                history[pointer] = last.copy(
                    text = newText,
                    afterCaret = afterCaret,
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

        history.add(UndoRedoElement(newText, pendingBeforeCaret, afterCaret, editKind, currentTime))

        // Enforce maximum stack depth by evicting the oldest entry
        if (history.size > maxStackSize) {
            history.removeAt(0)
        } else {
            pointer++
        }
    }

    /**
     * Reverts to previous text state and restores the `beforeCaret` cursor position.
     *
     * After undo, [pendingBeforeCaret] is updated internally so that any subsequent
     * undo or navigation is tracked from the correct restored position.
     *
     * @return [UndoResult] containing reverted text and restored caret position, or `null` if cannot undo.
     */
    fun undo(): UndoResult? {
        if (!canUndo) return null
        val element = history[pointer]
        pointer--
        val prevText = if (pointer >= 0) history[pointer].text else initialText
        val restoredCaret = element.beforeCaret
        pendingBeforeCaret = restoredCaret
        return UndoResult(text = prevText, caretState = restoredCaret)
    }

    /**
     * Re-applies next text state and restores the `afterCaret` cursor position.
     *
     * After redo, [pendingBeforeCaret] is updated internally so that any subsequent edit
     * is tracked from the correct restored position.
     *
     * @return [RedoResult] containing redone text and restored caret position, or `null` if cannot redo.
     */
    fun redo(): RedoResult? {
        if (!canRedo) return null
        pointer++
        val element = history[pointer]
        val restoredCaret = element.afterCaret
        pendingBeforeCaret = restoredCaret
        return RedoResult(text = element.text, caretState = restoredCaret)
    }

    /**
     * Determines whether a new edit should be coalesced (merged) into the most recent history entry.
     *
     * Implements all five modern IDE boundary rules:
     *
     * - **Rule 1 — Whitespace boundary**: A Space or Tab insertion is appended to the current group,
     *   then [pendingGroupBreak] is set so the NEXT non-delimiter character starts a fresh group.
     * - **Rule 2 — Delimiter boundary**: Punctuation is appended to the current group, then
     *   [pendingGroupBreak] is set so the NEXT character starts a fresh group.
     * - **Rule 3 — Direction change**: Switching between [EditKind.Insertion] and [EditKind.Deletion] breaks the group.
     * - **Rule 4 — Cursor discontinuity**: Non-sequential cursor position (different line or column jump > 1) breaks the group.
     * - **Rule 5 — Time pause**: Gap exceeding [coalesceTimeoutMs] breaks the group.
     *
     * Structural edits always return `false` (never coalesced).
     *
     * @param last The most recent history entry to evaluate coalescing against.
     * @param newText The incoming document text after the new edit.
     * @param afterCaret The cursor position after the new edit.
     * @param editKind The kind of the incoming edit.
     * @param currentTime The current system time in milliseconds.
     * @return `true` if the new edit should be merged into [last]; `false` if it starts a new group.
     */
    private fun shouldCoalesce(
        last: UndoRedoElement,
        newText: String,
        afterCaret: EditorCaretState,
        editKind: EditKind,
        currentTime: Long
    ): Boolean {

        if (editKind == EditKind.Structural || last.editKind == EditKind.Structural) return false

        if (pendingGroupBreak) {
            pendingGroupBreak = false
            return false
        }

        if (currentTime - last.timestamp > coalesceTimeoutMs) return false

        if (editKind != last.editKind) return false

        if (afterCaret.lineIndex != last.afterCaret.lineIndex) return false

        if (kotlin.math.abs(afterCaret.colIndex - last.afterCaret.colIndex) > 1) return false

        if (editKind == EditKind.Insertion) {
            val insertedChar = newText.getOrNull(afterCaret.colIndex - 1)
            if (insertedChar != null && (insertedChar.isWhitespace() || isDelimiter(insertedChar))) {
                pendingGroupBreak = true
                return true // Append the delimiter to the current group
            }
        }

        return true
    }

    /**
     * Returns `true` if the given character is a code delimiter that should break the current
     * typing burst into a new undo transaction. Matches VS Code and IntelliJ's delimiter set.
     *
     * @param ch The character to evaluate.
     */
    private fun isDelimiter(ch: Char): Boolean =
        ch in ";,.(){}[]=+-*/<>!&|^%~:`\""
}
