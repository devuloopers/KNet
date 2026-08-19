package com.devuloopers.knet.ui.desktop.codeeditor.document

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val editorHistoryTimeOrigin = TimeSource.Monotonic.markNow()

/**
 * Result of applying an undo or redo group.
 *
 * @property snapshot Document snapshot after the group was applied.
 * @property caret Suggested caret position after the operation.
 * @property changes Exact inverse or replay changes applied to reach [snapshot].
 */
data class EditorHistoryResult(
    val snapshot: EditorDocumentSnapshot,
    val caret: EditorPosition,
    val changes: List<EditorDocumentChange>
)

private data class EditorUndoGroup(
    val changes: MutableList<EditorDocumentChange>,
    var lastEditMillis: Long
)

/**
 * Delta-based undo and redo manager for [EditorDocument].
 *
 * History retains inserted and removed fragments rather than complete document snapshots. Adjacent
 * insertions or deletions are grouped until [coalescingTimeout] expires or [breakGroup] is called.
 *
 * @param maximumGroups Maximum number of undo groups retained.
 * @param coalescingTimeout Maximum pause between adjacent edits in one group.
 */
class EditorUndoManager(
    private val maximumGroups: Int = 100,
    private val coalescingTimeout: Duration = 1.seconds
) {
    init {
        require(maximumGroups > 0) { "Undo history capacity must be positive." }
        require(!coalescingTimeout.isNegative()) { "Undo coalescing timeout must not be negative." }
    }

    private val undoGroups = ArrayDeque<EditorUndoGroup>()
    private val redoGroups = ArrayDeque<EditorUndoGroup>()
    private var forceNextGroup = false

    /** Returns `true` when at least one edit group can be undone. */
    val canUndo: Boolean
        get() = undoGroups.isNotEmpty()

    /** Returns `true` when at least one edit group can be redone. */
    val canRedo: Boolean
        get() = redoGroups.isNotEmpty()

    /**
     * Records one accepted document change.
     *
     * @param change Change returned by [EditorDocument.apply].
     */
    fun record(change: EditorDocumentChange) {
        if (change.removedText == change.insertedText) return
        val now = currentHistoryTimeMillis()
        val lastGroup = undoGroups.lastOrNull()
        if (!forceNextGroup && lastGroup != null && shouldCoalesce(lastGroup, change, now)) {
            lastGroup.changes += change
            lastGroup.lastEditMillis = now
        } else {
            undoGroups.addLast(EditorUndoGroup(mutableListOf(change), now))
            while (undoGroups.size > maximumGroups) undoGroups.removeFirst()
        }
        forceNextGroup = change.kind == EditorEditKind.Structural || change.kind == EditorEditKind.Replacement
        redoGroups.clear()
    }

    /**
     * Records several already-applied changes as one atomic undo group.
     *
     * Changes must be provided in the same order in which they were applied to the document.
     *
     * @param changes Non-empty ordered changes that form one user command.
     */
    fun recordGroup(changes: List<EditorDocumentChange>) {
        val effectiveChanges = changes.filter { it.beforeVersion != it.afterVersion }
        if (effectiveChanges.isEmpty()) return
        undoGroups.addLast(EditorUndoGroup(effectiveChanges.toMutableList(), currentHistoryTimeMillis()))
        while (undoGroups.size > maximumGroups) undoGroups.removeFirst()
        redoGroups.clear()
        forceNextGroup = true
    }

    /** Forces the next recorded edit to start a separate undo group. */
    fun breakGroup() {
        forceNextGroup = true
    }

    /** Clears all undo and redo history. */
    fun clear() {
        undoGroups.clear()
        redoGroups.clear()
        forceNextGroup = false
    }

    /**
     * Reverts the newest undo group.
     *
     * @param document Document that owns the recorded versions.
     * @return Updated snapshot and caret, or `null` when no undo is available.
     */
    fun undo(document: EditorDocument): EditorHistoryResult? {
        val group = undoGroups.removeLastOrNull() ?: return null
        val appliedChanges = buildList(group.changes.size) {
            for (change in group.changes.asReversed()) {
                add(
                    document.apply(
                        EditorTextEdit(
                            range = change.afterRange,
                            replacement = change.removedText,
                            kind = EditorEditKind.Structural
                        )
                    )
                )
            }
        }
        redoGroups.addLast(group)
        forceNextGroup = true
        return EditorHistoryResult(
            snapshot = document.snapshot,
            caret = group.changes.first().beforeRange.start,
            changes = appliedChanges
        )
    }

    /**
     * Reapplies the newest redo group.
     *
     * @param document Document that owns the recorded versions.
     * @return Updated snapshot and caret, or `null` when no redo is available.
     */
    fun redo(document: EditorDocument): EditorHistoryResult? {
        val group = redoGroups.removeLastOrNull() ?: return null
        val appliedChanges = buildList(group.changes.size) {
            for (change in group.changes) {
                add(
                    document.apply(
                        EditorTextEdit(
                            range = change.beforeRange,
                            replacement = change.insertedText,
                            kind = EditorEditKind.Structural
                        )
                    )
                )
            }
        }
        undoGroups.addLast(group)
        forceNextGroup = true
        return EditorHistoryResult(
            snapshot = document.snapshot,
            caret = group.changes.last().afterRange.end,
            changes = appliedChanges
        )
    }

    private fun shouldCoalesce(group: EditorUndoGroup, change: EditorDocumentChange, now: Long): Boolean {
        val previous = group.changes.last()
        if (previous.kind != change.kind) return false
        if (change.kind != EditorEditKind.Insertion && change.kind != EditorEditKind.Deletion) return false
        if (now - group.lastEditMillis > coalescingTimeout.inWholeMilliseconds) return false
        return when (change.kind) {
            EditorEditKind.Insertion -> previous.afterRange.end == change.beforeRange.start
            EditorEditKind.Deletion -> {
                change.beforeRange.end == previous.afterRange.start ||
                    change.beforeRange.start == previous.afterRange.start
            }
            EditorEditKind.Replacement,
            EditorEditKind.Structural -> false
        }
    }
}

private fun currentHistoryTimeMillis(): Long = editorHistoryTimeOrigin.elapsedNow().inWholeMilliseconds
