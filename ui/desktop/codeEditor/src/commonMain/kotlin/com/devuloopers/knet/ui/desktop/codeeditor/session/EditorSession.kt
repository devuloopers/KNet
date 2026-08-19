package com.devuloopers.knet.ui.desktop.codeeditor.session

import com.devuloopers.knet.ui.desktop.codeeditor.document.ChunkedEditorDocument
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocument
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentChange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorEditKind
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorTextEdit
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorUndoManager

/**
 * Origin of an [EditorSessionEvent].
 */
enum class EditorChangeOrigin {
    /** The user or a command changed document content. */
    User,

    /** The undo manager reverted a recorded edit group. */
    Undo,

    /** The undo manager reapplied a reverted edit group. */
    Redo,

    /** A controlling consumer replaced the complete editor value. */
    External
}

/**
 * Immutable editor-session notification.
 *
 * @property snapshot Latest document snapshot.
 * @property documentChanges Exact ordered text changes. Empty only for caret/selection-only state changes.
 * @property caret Active caret position.
 * @property selection Directional active selection, or `null` when no range is selected.
 * @property origin Source of this state transition.
 */
data class EditorSessionEvent(
    val snapshot: EditorDocumentSnapshot,
    val documentChanges: List<EditorDocumentChange>,
    val caret: EditorPosition,
    val selection: EditorSelection?,
    val origin: EditorChangeOrigin
) {
    /** Single text change when this event represents exactly one edit, otherwise `null`. */
    val documentChange: EditorDocumentChange?
        get() = documentChanges.singleOrNull()
}

/**
 * Listener notified synchronously after an editor-session transition.
 */
fun interface EditorSessionListener {
    /**
     * Handles a new immutable session event.
     *
     * @param event Latest document and selection state.
     */
    fun onSessionChanged(event: EditorSessionEvent)
}

/**
 * Disposable listener registration returned by [EditorSession.subscribe].
 */
fun interface EditorSessionSubscription {
    /** Stops future events from being delivered to the registered listener. */
    fun cancel()
}

/**
 * UI-neutral single source of truth for one editor document, caret, selection, and edit history.
 *
 * The session accepts position/range commands and emits immutable versioned snapshots. It does not
 * depend on Compose, HTTP models, persistence, or a specific language implementation, allowing the
 * same editor foundation to be reused by desktop, Android, iOS, or other Kotlin frontends.
 *
 * Mutations and subscriptions must be confined to the owning UI/controller thread. Immutable
 * [EditorDocumentSnapshot] values may be read by background language and search workers.
 *
 * @param initialText Initial document content.
 * @param document Mutable document implementation. Supplying this permits alternative rope or
 * piece-tree implementations without changing the session API.
 * @param history Delta-based history manager used for undo and redo.
 */
class EditorSession(
    initialText: String = "",
    private val document: EditorDocument = ChunkedEditorDocument(initialText),
    private val history: EditorUndoManager = EditorUndoManager()
) {
    private val listeners = mutableSetOf<EditorSessionListener>()

    /** Latest immutable document snapshot. */
    val snapshot: EditorDocumentSnapshot
        get() = document.snapshot

    /** Current caret position. */
    var caret: EditorPosition = EditorPosition(0, 0)
        private set

    /** Current non-empty selection range, or `null`. */
    var selection: EditorSelection? = null
        private set

    /** Returns `true` when at least one edit group can be undone. */
    val canUndo: Boolean
        get() = history.canUndo

    /** Returns `true` when at least one edit group can be redone. */
    val canRedo: Boolean
        get() = history.canRedo

    /**
     * Registers a synchronous observer.
     *
     * @param listener Observer to notify after later transitions.
     * @return Subscription whose [EditorSessionSubscription.cancel] method removes the observer.
     */
    fun subscribe(listener: EditorSessionListener): EditorSessionSubscription {
        listeners += listener
        return EditorSessionSubscription { listeners -= listener }
    }

    /**
     * Moves the caret and clears any active selection.
     *
     * @param position Requested position, clamped to the latest snapshot.
     */
    fun moveCaret(position: EditorPosition) {
        caret = snapshot.clamp(position)
        selection = null
        history.breakGroup()
        publish(documentChanges = emptyList(), origin = EditorChangeOrigin.User)
    }

    /**
     * Updates the active selection and moves the caret to its end.
     *
     * @param value Directional selection, or `null` to clear it. Empty selections become a caret.
     */
    fun select(value: EditorSelection?) {
        if (value == null) {
            selection = null
        } else {
            val anchor = snapshot.clamp(value.anchor)
            val active = snapshot.clamp(value.active)
            selection = EditorSelection(anchor, active).takeUnless(EditorSelection::isEmpty)
            caret = active
        }
        history.breakGroup()
        publish(documentChanges = emptyList(), origin = EditorChangeOrigin.User)
    }

    /**
     * Applies one user edit and records it in delta history.
     *
     * @param edit Text replacement to apply.
     * @param caretAfter Optional caret position for compound edits such as automatic bracket pairs.
     * @return Accepted change. A no-op has equal before and after versions.
     */
    fun apply(edit: EditorTextEdit, caretAfter: EditorPosition? = null): EditorDocumentChange {
        val change = document.apply(edit)
        if (change.afterVersion == change.beforeVersion) return change
        history.record(change)
        caret = snapshot.clamp(caretAfter ?: change.afterRange.end)
        selection = null
        publish(listOf(change), EditorChangeOrigin.User)
        return change
    }

    /**
     * Applies non-overlapping edits as one undoable command.
     *
     * Edits are sorted from the end of the document toward the start, preserving all input ranges
     * while mutations are applied. This is suitable for replace-all and multi-cursor-style commands.
     *
     * @param edits Edits expressed against the current snapshot.
     * @return Ordered accepted changes in their actual application order.
     */
    fun applyBatch(edits: List<EditorTextEdit>): List<EditorDocumentChange> {
        if (edits.isEmpty()) return emptyList()
        val ordered = edits.sortedByDescending { it.range.start }
        requireNonOverlapping(ordered)
        val changes = ordered.map(document::apply).filter { it.beforeVersion != it.afterVersion }
        if (changes.isEmpty()) return emptyList()
        history.recordGroup(changes)
        caret = changes.last().afterRange.end
        selection = null
        publish(changes, EditorChangeOrigin.User)
        return changes
    }

    /**
     * Replaces the active selection or inserts text at the caret.
     *
     * @param text Text to insert.
     * @param kind Edit category used for undo grouping.
     * @return Accepted document change.
     */
    fun insert(text: String, kind: EditorEditKind = EditorEditKind.Insertion): EditorDocumentChange {
        return apply(EditorTextEdit(selection?.range ?: EditorRange.caret(caret), text, kind))
    }

    /**
     * Efficiently replaces one logical line by calculating its smallest changed range.
     *
     * @param lineIndex Zero-based line index.
     * @param newText New line content without a newline.
     * @return Accepted change, or `null` when the line is unchanged or invalid.
     */
    fun replaceLine(lineIndex: Int, newText: String): EditorDocumentChange? {
        if (lineIndex !in 0 until snapshot.lineCount) return null
        val oldText = snapshot.line(lineIndex)
        if (oldText == newText) return null

        var prefixLength = 0
        val commonLimit = minOf(oldText.length, newText.length)
        while (prefixLength < commonLimit && oldText[prefixLength] == newText[prefixLength]) prefixLength++

        var suffixLength = 0
        while (
            suffixLength < oldText.length - prefixLength &&
            suffixLength < newText.length - prefixLength &&
            oldText[oldText.lastIndex - suffixLength] == newText[newText.lastIndex - suffixLength]
        ) {
            suffixLength++
        }

        val removedEnd = oldText.length - suffixLength
        val insertedEnd = newText.length - suffixLength
        val replacement = newText.substring(prefixLength, insertedEnd)
        val kind = when {
            removedEnd == prefixLength -> EditorEditKind.Insertion
            replacement.isEmpty() -> EditorEditKind.Deletion
            else -> EditorEditKind.Replacement
        }
        return apply(
            EditorTextEdit(
                range = EditorRange(
                    EditorPosition(lineIndex, prefixLength),
                    EditorPosition(lineIndex, removedEnd)
                ),
                replacement = replacement,
                kind = kind
            )
        )
    }

    /**
     * Splits a line at a column and optionally inserts indentation on the new line.
     *
     * @param position Split position.
     * @param indentation Indentation inserted after the newline.
     * @return Accepted structural change.
     */
    fun splitLine(position: EditorPosition, indentation: String = ""): EditorDocumentChange {
        val safePosition = snapshot.clamp(position)
        return apply(
            EditorTextEdit(
                range = EditorRange.caret(safePosition),
                replacement = "\n$indentation",
                kind = EditorEditKind.Structural
            )
        )
    }

    /**
     * Merges a line with its previous logical line.
     *
     * @param lineIndex Line whose preceding newline should be removed.
     * @return Accepted structural change, or `null` for the first or an invalid line.
     */
    fun mergeWithPreviousLine(lineIndex: Int): EditorDocumentChange? {
        if (lineIndex !in 1 until snapshot.lineCount) return null
        val previousLength = snapshot.line(lineIndex - 1).length
        return apply(
            EditorTextEdit(
                range = EditorRange(
                    EditorPosition(lineIndex - 1, previousLength),
                    EditorPosition(lineIndex, 0)
                ),
                replacement = "",
                kind = EditorEditKind.Structural
            )
        )
    }

    /**
     * Replaces complete content from an external controlled-state owner and clears undo history.
     *
     * @param text New complete document text.
     */
    fun replaceAllFromExternal(text: String) {
        if (snapshot.text() == text) return
        val change = document.replaceAll(text)
        history.clear()
        caret = document.snapshot.clamp(caret)
        selection = null
        publish(listOf(change), EditorChangeOrigin.External)
    }

    /**
     * Reverts the newest edit group.
     *
     * @return `true` when a group was reverted.
     */
    fun undo(): Boolean {
        val result = history.undo(document) ?: return false
        caret = result.caret
        selection = null
        publish(documentChanges = result.changes, origin = EditorChangeOrigin.Undo)
        return true
    }

    /**
     * Reapplies the newest reverted edit group.
     *
     * @return `true` when a group was reapplied.
     */
    fun redo(): Boolean {
        val result = history.redo(document) ?: return false
        caret = result.caret
        selection = null
        publish(documentChanges = result.changes, origin = EditorChangeOrigin.Redo)
        return true
    }

    private fun requireNonOverlapping(edits: List<EditorTextEdit>) {
        for (index in 0 until edits.lastIndex) {
            val later = edits[index]
            val earlier = edits[index + 1]
            require(earlier.range.end <= later.range.start) { "Batch editor ranges must not overlap." }
        }
    }

    private fun publish(documentChanges: List<EditorDocumentChange>, origin: EditorChangeOrigin) {
        val event = EditorSessionEvent(snapshot, documentChanges, caret, selection, origin)
        listeners.toList().forEach { it.onSessionChanged(event) }
    }
}
